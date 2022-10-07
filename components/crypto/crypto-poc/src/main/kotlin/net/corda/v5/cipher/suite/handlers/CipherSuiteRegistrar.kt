package net.corda.v5.cipher.suite.handlers

import net.corda.v5.cipher.suite.CipherSuite

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

