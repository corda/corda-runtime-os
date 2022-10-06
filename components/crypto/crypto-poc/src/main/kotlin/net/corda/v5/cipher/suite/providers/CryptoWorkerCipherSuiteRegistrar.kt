package net.corda.v5.cipher.suite.providers

import net.corda.v5.cipher.suite.CryptoWorkerCipherSuite

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