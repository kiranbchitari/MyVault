package com.myvault.crypto

/**
 * Failures raised by the crypto core. Messages are fixed strings: they never contain key
 * material, plaintext or passphrase data, so they are safe to surface or log.
 */
sealed class VaultCryptoException(message: String) : Exception(message)

/**
 * The authentication tag did not verify: the key is wrong, or the ciphertext, header or
 * associated data was modified. These cases are deliberately indistinguishable.
 */
class AuthenticationFailedException : VaultCryptoException("Authentication failed")

/** The data was written by a format or algorithm version this build does not understand. */
class UnsupportedFormatException(what: String) : VaultCryptoException("Unsupported $what")

/** The data is structurally invalid (truncated, or carrying out-of-range parameters). */
class MalformedDataException(what: String) : VaultCryptoException("Malformed $what")
