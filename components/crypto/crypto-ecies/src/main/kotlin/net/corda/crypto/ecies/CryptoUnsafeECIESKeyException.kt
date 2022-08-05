package net.corda.crypto.ecies

/**
 * Signals that the generated key pair is not safe to be used in ECIES.
 */
class CryptoUnsafeECIESKeyException(message: String) : IllegalArgumentException(message)