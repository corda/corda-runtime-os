package net.corda.v5.cipher.suite.schemes

/**
 * A key usages, such as for signing or deriving a shared secret or any combination.
 */
enum class KeySchemeCapability {
    SIGN,
    SHARED_SECRET_DERIVATION
}