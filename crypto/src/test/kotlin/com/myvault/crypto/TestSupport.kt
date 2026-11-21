package com.myvault.crypto

internal fun hex(s: String): ByteArray =
    s.filterNot(Char::isWhitespace).chunked(2).map { it.toInt(16).toByte() }.toByteArray()

internal fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

/** Cheap Argon2 parameters so the suite stays fast. Production defaults are tested separately. */
internal val FAST_PARAMS = Argon2Params(memoryKiB = 256, iterations = 1, parallelism = 1)

internal fun ByteArray.indexOfSubArray(needle: ByteArray): Int {
    if (needle.isEmpty() || needle.size > size) return -1
    outer@ for (i in 0..size - needle.size) {
        for (j in needle.indices) if (this[i + j] != needle[j]) continue@outer
        return i
    }
    return -1
}
