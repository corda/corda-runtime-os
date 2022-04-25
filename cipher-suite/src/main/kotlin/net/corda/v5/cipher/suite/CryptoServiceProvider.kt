package net.corda.v5.cipher.suite

import net.corda.v5.crypto.exceptions.CryptoServiceException

/**
 * Factory to create new instances of the crypto service.
 */
interface CryptoServiceProvider<T : Any> {
    /**
     * The name used to resolve current provider by crypto service factory. Should match `cryptoServiceName` in the configuration.
     */
    val name: String

    /**
     * Class for crypto service specific configuration which must be defined together with particular [CryptoService] implementation.
     * Used by crypto service factory to parse corresponding configuration file via Config.parseAs().
     */
    val configType: Class<T>

    /**
     * Returns an instance of the [CryptoService].
     * @param config crypto service configuration
     * @throws [CryptoServiceException] for general cryptographic exceptions.
     */
    fun getInstance(config: T): CryptoService
}
