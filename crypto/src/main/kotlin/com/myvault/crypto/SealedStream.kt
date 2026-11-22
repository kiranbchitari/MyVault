package com.myvault.crypto

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer

/**
 * A stream of independently authenticated frames, so large data can be encrypted and decrypted
 * with bounded memory.
 *
 * ```
 * frame := final (1 byte: 0 or 1) | length (4 bytes, big-endian) | VaultCipher envelope
 * ```
 *
 * Each envelope's associated data is `label || streamId || index (8 bytes) || final`. A frame
 * therefore cannot be reordered, dropped, duplicated or moved to another stream, and a stream
 * that does not end with a final frame is rejected as truncated.
 */
object SealedStream {
    const val MAX_FRAME_PLAINTEXT_BYTES = 1 shl 20
    private const val MAX_SEALED_BYTES = MAX_FRAME_PLAINTEXT_BYTES + VaultCipher.MIN_SEALED_BYTES

    internal fun context(label: ByteArray, streamId: ByteArray, index: Long, final: Boolean): ByteArray =
        label + streamId + ByteBuffer.allocate(9).putLong(index).put(if (final) 1 else 0).array()

    class Writer(
        output: OutputStream,
        private val cipher: VaultCipher,
        private val key: SecretBytes,
        private val streamId: ByteArray,
        label: String,
    ) {
        private val out = DataOutputStream(output)
        private val labelBytes = label.toByteArray(Charsets.US_ASCII)
        private var index = 0L
        private var finished = false

        fun write(plaintext: ByteArray) = writeFrame(plaintext, final = false)

        /** Writes the terminating frame. Nothing may be written afterwards. */
        fun finish() {
            writeFrame(ByteArray(0), final = true)
            out.flush()
        }

        private fun writeFrame(plaintext: ByteArray, final: Boolean) {
            check(!finished) { "stream already finished" }
            require(plaintext.size <= MAX_FRAME_PLAINTEXT_BYTES) { "frame too large" }
            val sealed = cipher.seal(key, 0, plaintext, context(labelBytes, streamId, index, final))
            out.writeByte(if (final) 1 else 0)
            out.writeInt(sealed.size)
            out.write(sealed)
            index++
            finished = final
        }
    }

    class Reader(
        input: InputStream,
        private val cipher: VaultCipher,
        private val key: SecretBytes,
        private val streamId: ByteArray,
        label: String,
    ) {
        private val source = DataInputStream(input)
        private val labelBytes = label.toByteArray(Charsets.US_ASCII)
        private var index = 0L
        private var finished = false

        /**
         * Returns the next frame's plaintext, or null once the final frame has been verified.
         *
         * @throws AuthenticationFailedException wrong key, or a frame was modified or moved
         * @throws MalformedDataException truncated stream or trailing data
         */
        fun read(): ByteArray? {
            if (finished) return null
            val final: Boolean
            val sealed: ByteArray
            try {
                final = when (source.readUnsignedByte()) {
                    0 -> false
                    1 -> true
                    else -> throw MalformedDataException("frame header")
                }
                val length = source.readInt()
                if (length < VaultCipher.MIN_SEALED_BYTES || length > MAX_SEALED_BYTES) throw MalformedDataException("frame length")
                sealed = ByteArray(length)
                source.readFully(sealed)
            } catch (_: EOFException) {
                throw MalformedDataException("stream (truncated)")
            }
            val plaintext = cipher.open(key, sealed, context(labelBytes, streamId, index, final))
            index++
            if (!final) return plaintext
            if (plaintext.isNotEmpty() || source.read() != -1) throw MalformedDataException("stream (trailing data)")
            finished = true
            return null
        }
    }
}
