package com.myvault.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.SecureRandom

class SealedStreamTest {
    private val random = SecureRandom()
    private val cipher = VaultCipher(random)
    private val key = SecretBytes.random(32, random)
    private val streamId = ByteArray(16).also(random::nextBytes)
    private val label = "test/stream"

    private fun write(frames: List<ByteArray>, finish: Boolean = true): ByteArray {
        val out = ByteArrayOutputStream()
        val writer = SealedStream.Writer(out, cipher, key, streamId, label)
        frames.forEach(writer::write)
        if (finish) writer.finish()
        return out.toByteArray()
    }

    private fun readAll(bytes: ByteArray, withKey: SecretBytes = key, id: ByteArray = streamId, withLabel: String = label): List<ByteArray> {
        val reader = SealedStream.Reader(ByteArrayInputStream(bytes), cipher, withKey, id, withLabel)
        return generateSequence { reader.read() }.toList()
    }

    private val frames = listOf("first".toByteArray(), ByteArray(0), ByteArray(70_000) { it.toByte() }, "last".toByteArray())

    /** Byte ranges of each frame in the encoded stream. */
    private fun frameRanges(bytes: ByteArray): List<IntRange> {
        val ranges = mutableListOf<IntRange>()
        var offset = 0
        while (offset < bytes.size) {
            val length = java.nio.ByteBuffer.wrap(bytes, offset + 1, 4).int
            ranges += offset until offset + 5 + length
            offset += 5 + length
        }
        return ranges
    }

    @Test
    fun `frames round-trip in order`() {
        val decoded = readAll(write(frames))
        assertEquals(frames.size, decoded.size)
        frames.zip(decoded).forEach { (expected, actual) -> assertArrayEquals(expected, actual) }
    }

    @Test
    fun `reading past the end keeps returning null`() {
        val reader = SealedStream.Reader(ByteArrayInputStream(write(frames)), cipher, key, streamId, label)
        repeat(frames.size) { reader.read() }
        assertNull(reader.read())
        assertNull(reader.read())
    }

    @Test
    fun `a stream without its final frame is rejected as truncated`() {
        assertThrows(MalformedDataException::class.java) { readAll(write(frames, finish = false)) }
        val complete = write(frames)
        val lastFrame = frameRanges(complete).last()
        assertThrows(MalformedDataException::class.java) { readAll(complete.copyOf(lastFrame.first)) }
        assertThrows(MalformedDataException::class.java) { readAll(complete.copyOf(complete.size - 3)) }
    }

    @Test
    fun `dropping, swapping or duplicating frames is rejected`() {
        val bytes = write(frames)
        val ranges = frameRanges(bytes)
        fun slice(range: IntRange) = bytes.copyOfRange(range.first, range.last + 1)

        val dropped = slice(ranges[0]) + ranges.drop(2).map(::slice).reduce(ByteArray::plus)
        assertThrows(AuthenticationFailedException::class.java) { readAll(dropped) }

        val swapped = slice(ranges[1]) + slice(ranges[0]) + ranges.drop(2).map(::slice).reduce(ByteArray::plus)
        assertThrows(AuthenticationFailedException::class.java) { readAll(swapped) }

        val duplicated = slice(ranges[0]) + slice(ranges[0]) + ranges.drop(1).map(::slice).reduce(ByteArray::plus)
        assertThrows(AuthenticationFailedException::class.java) { readAll(duplicated) }
    }

    @Test
    fun `ending the stream early by forging the final flag is rejected`() {
        val bytes = write(frames)
        val second = frameRanges(bytes)[1]
        val forged = bytes.copyOf(second.last + 1).also { it[second.first] = 1 }
        assertThrows(AuthenticationFailedException::class.java) { readAll(forged) }
    }

    @Test
    fun `data after the final frame is rejected`() {
        assertThrows(MalformedDataException::class.java) { readAll(write(frames) + byteArrayOf(0)) }
    }

    @Test
    fun `modifying any byte is rejected`() {
        val bytes = write(listOf("alpha".toByteArray(), "beta".toByteArray()))
        for (i in bytes.indices) {
            val tampered = bytes.copyOf().also { it[i] = (it[i].toInt() xor 0x01).toByte() }
            assertThrows("byte $i", VaultCryptoException::class.java) { readAll(tampered) }
        }
    }

    @Test
    fun `wrong key, stream id or label is rejected`() {
        val bytes = write(frames)
        assertThrows(AuthenticationFailedException::class.java) { readAll(bytes, withKey = SecretBytes.random(32, random)) }
        assertThrows(AuthenticationFailedException::class.java) { readAll(bytes, id = ByteArray(16)) }
        assertThrows(AuthenticationFailedException::class.java) { readAll(bytes, withLabel = "other/stream") }
    }

    @Test
    fun `an absurd frame length is rejected without allocating it`() {
        val bytes = write(frames)
        bytes[1] = 0x7F
        assertThrows(MalformedDataException::class.java) { readAll(bytes) }
    }
}
