package net.corda.crypto.poc.ciphersuite2.publicapi.providers

import net.corda.crypto.poc.ciphersuite2.publicapi.CipherSuite

/**
 * Registers kee scheme and handlers for in-process handlers.
 * Utilises double dispatch pattern.
 * Implementation must be @Component
 */
interface CipherSuiteRegistrar {
    /**
     * Will be called by the platform, the implementation has to call [CipherSuite.register]
     */
    fun registerWith(suite: CipherSuite)
}

