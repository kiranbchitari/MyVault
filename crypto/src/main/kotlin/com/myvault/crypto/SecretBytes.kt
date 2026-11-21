package com.myvault.crypto

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Holder for key material that can be wiped from memory.
 *
 * Zeroization is best effort: the JVM may have copied the array during garbage collection, and
 * JCA key objects keep their own internal copy. The wrapper still ensures that key bytes are
 * never printed, and that a locked vault no longer holds a usable reference to them.
 */
class SecretBytes private constructor(private var bytes: ByteArray?) : AutoCloseable {

    val isDestroyed: Boolean
        @Synchronized get() = bytes == null

    val size: Int
        @Synchronized get() = live().size

    /** Runs [block] with the raw bytes. The array must not be retained or modified by the caller. */
    @Synchronized
    fun <T> access(block: (ByteArray) -> T): T = block(live())

    @Synchronized
    fun copy(): SecretBytes = SecretBytes(live().copyOf())

    /** Constant-time comparison. */
    @Synchronized
    fun contentEquals(other: SecretBytes): Boolean {
        val mine = live()
        return other.access { MessageDigest.isEqual(mine, it) }
    }

    @Synchronized
    override fun close() {
        bytes?.fill(0)
        bytes = null
    }

    override fun toString(): String = "SecretBytes(REDACTED)"

    private fun live(): ByteArray = bytes ?: throw IllegalStateException("Secret has been destroyed")

    companion object {
        /** Takes ownership of [bytes]: the array is zeroed when the returned secret is closed. */
        fun wrap(bytes: ByteArray): SecretBytes = SecretBytes(bytes)

        fun random(size: Int, random: SecureRandom): SecretBytes {
            require(size > 0) { "size must be positive" }
            return SecretBytes(ByteArray(size).also(random::nextBytes))
        }
    }
}
