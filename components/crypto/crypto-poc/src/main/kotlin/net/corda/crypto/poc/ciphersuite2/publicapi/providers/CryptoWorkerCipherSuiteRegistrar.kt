package net.corda.crypto.poc.ciphersuite2.publicapi.providers

import net.corda.crypto.poc.ciphersuite2.publicapi.CryptoWorkerCipherSuite

/**
 * Registers kee scheme and handlers for crypto worker handlers.
 * Utilises double dispatch pattern.
 * Implementation must be @Component
 */
interface CryptoWorkerCipherSuiteRegistrar {
    /**
     * Will be called by the platform, the implementation has to call [CryptoWorkerCipherSuite.register]
     */
    fun registerWith(suite: CryptoWorkerCipherSuite)
}