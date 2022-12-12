package net.corda.crypto.hes

/**
 * Signals that the generated key pair is not safe to be used in Hybrid Encryption Scheme.
 */
class CryptoUnsafeHESKeyException(message: String) : IllegalArgumentException(message)