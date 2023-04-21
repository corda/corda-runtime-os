package net.corda.crypto.cipher.suite

/**
 * [CryptoService] extensions.
 */
enum class CryptoServiceExtensions {
    /**
     * Specifies that the createWrappingKey operation is required and the HSM supports key wrapping.
     * The wrapping key may not be required in situations when HSM supports the wrapped keys natively or
     * wrapping key is global.
     */
    REQUIRE_WRAPPING_KEY,

    /**
     * The service supports the key deletion.
     */
    DELETE_KEYS,

    /**
     * Specifies that the implementation supports Diffie–Hellman key agreement  for qualifying key schemes.
     */
    SHARED_SECRET_DERIVATION
}