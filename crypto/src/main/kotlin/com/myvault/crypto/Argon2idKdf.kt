package com.myvault.crypto

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.nio.CharBuffer
import java.text.Normalizer

/**
 * Argon2id cost parameters. They are stored next to the wrapped key so they can be raised for
 * new vaults (or on a passphrase change) without breaking existing ones.
 */
data class Argon2Params(val memoryKiB: Int, val iterations: Int, val parallelism: Int) {
    init {
        require(parallelism in 1..MAX_PARALLELISM) { "parallelism out of range" }
        require(iterations in 1..MAX_ITERATIONS) { "iterations out of range" }
        require(memoryKiB in (8 * parallelism)..MAX_MEMORY_KIB) { "memory out of range" }
    }

    companion object {
        // Upper bounds applied to parameters read from storage, so damaged or hostile data
        // cannot make an unlock attempt allocate unbounded memory or run for hours.
        const val MAX_MEMORY_KIB = 256 * 1024
        const val MAX_ITERATIONS = 64
        const val MAX_PARALLELISM = 16

        /**
         * 64 MiB, 3 passes: the memory-constrained profile from RFC 9106 section 4. Parallelism
         * is 1 because the Bouncy Castle implementation computes lanes sequentially; extra lanes
         * would help an attacker with real threads without making it any slower for them.
         */
        val DEFAULT = Argon2Params(memoryKiB = 64 * 1024, iterations = 3, parallelism = 1)
    }
}

/** Passphrase-based key derivation: Argon2id v1.3 via Bouncy Castle. */
object Argon2idKdf {
    const val MIN_SALT_BYTES = 16

    /**
     * Derives a key from [passphrase]. The passphrase is NFKC-normalized and UTF-8 encoded
     * first, so the same passphrase typed on a different keyboard or device derives the same key.
     * The caller keeps ownership of [passphrase] and should wipe it afterwards.
     */
    fun deriveKey(
        passphrase: CharArray,
        salt: ByteArray,
        params: Argon2Params,
        lengthBytes: Int = VaultCipher.KEY_BYTES,
    ): SecretBytes {
        require(passphrase.isNotEmpty()) { "passphrase must not be empty" }
        require(salt.size >= MIN_SALT_BYTES) { "salt too short" }
        val encoded = encodePassphrase(passphrase)
        try {
            return SecretBytes.wrap(hash(encoded, salt, params, lengthBytes))
        } finally {
            encoded.fill(0)
        }
    }

    internal fun hash(
        password: ByteArray,
        salt: ByteArray,
        params: Argon2Params,
        lengthBytes: Int,
        secret: ByteArray? = null,
        additional: ByteArray? = null,
    ): ByteArray {
        val builder = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withSalt(salt)
            .withMemoryAsKB(params.memoryKiB)
            .withIterations(params.iterations)
            .withParallelism(params.parallelism)
        if (secret != null) builder.withSecret(secret)
        if (additional != null) builder.withAdditional(additional)
        val out = ByteArray(lengthBytes)
        Argon2BytesGenerator().apply { init(builder.build()) }.generateBytes(password, out)
        return out
    }

    internal fun encodePassphrase(passphrase: CharArray): ByteArray {
        // Normalizer returns an immutable String, so one unwipeable copy of the passphrase lives
        // until garbage collection. Accepted in exchange for cross-device recoverability.
        val normalized = Normalizer.normalize(CharBuffer.wrap(passphrase), Normalizer.Form.NFKC)
        val buffer = Charsets.UTF_8.encode(normalized)
        val out = ByteArray(buffer.remaining())
        buffer.get(out)
        if (buffer.hasArray()) buffer.array().fill(0)
        return out
    }
}
