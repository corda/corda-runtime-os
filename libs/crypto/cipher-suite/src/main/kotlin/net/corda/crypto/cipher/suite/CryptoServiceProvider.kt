package net.corda.crypto.cipher.suite

import net.corda.libs.configuration.SmartConfig

/**
 * Factory to create new instances of the [CryptoService].
 */
interface CryptoServiceProvider {
    /**
     * The name used to resolve current provider by crypto service factory.
     */
    val name: String

    /**
     * Creates a new instance of the [CryptoService] implementation.
     *
     * @param config crypto service configuration
     * @return An instance of the [CryptoService].
     *
     * @throws [net.corda.v5.crypto.exceptions.CryptoException] for general cryptographic exceptions.
     */
    fun getInstance(config: SmartConfig): CryptoService
}
