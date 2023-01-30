package net.corda.crypto.cipher.suite

/**
 * Factory to create new instances of the [CryptoService].
 */
interface CryptoServiceProvider<T : Any> {
    /**
     * The name used to resolve current provider by crypto service factory.
     */
    val name: String

    /**
     * Class for crypto service specific configuration which must be defined together with particular [CryptoService]
     * implementation.
     */
    val configType: Class<T>

    /**
     * Creates a new instance of the [CryptoService] implementation.
     *
     * @param config crypto service configuration
     * @return An instance of the [CryptoService].
     *
     * @throws [net.corda.v5.crypto.exceptions.CryptoException] for general cryptographic exceptions.
     */
    fun getInstance(config: T): CryptoService
}
