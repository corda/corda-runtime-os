package net.corda.crypto.tck

/**
 * Compliance test suite type.
 */
enum class ComplianceTestType {
    /**
     * CryptoService level test, like generating key pairs, signing, etc.
     */
    CRYPTO_SERVICE,

    /**
     * Tests ensuring that the time between operations doesn't affect the execution (as most of all HSMs have
     * a concept of a login with provided credentials).
     */
    SESSION_INACTIVITY
}