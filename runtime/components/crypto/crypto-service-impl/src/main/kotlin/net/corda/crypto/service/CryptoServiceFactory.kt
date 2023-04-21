package net.corda.crypto.service

import net.corda.crypto.cipher.suite.CryptoService
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.Lifecycle

/**
 * Provides instances of fully configured [CryptoService].
 */
interface CryptoServiceFactory : Lifecycle {
    /**
     * Returns [CryptoServiceRef] containing [CryptoService] for a given tenant and category.
     * Once created the information and instance are cached.
     */
    fun findInstance(tenantId: String, category: String): CryptoServiceRef

    /**
     * Returns instance of [CryptoService] for specified HSM id.
     * Once created the information and instance are cached.
     */
    fun getInstance(hsmId: String): CryptoService

    /**
     * Provide bootstrap configuration to the crypto service factory.
     *
     * This should be called by the application, providing enough initial configuration to define which HSM the
     * service should handle. Other services will not need to call this.
     *
     * Calling this multiple times with different config will result in an error on processing the event.
     * If the configuration does not contain all required information it will result in an error as well.
     *
     * @param config The bootstrap configuration.
     */
    fun bootstrapConfig(config: SmartConfig)
}
