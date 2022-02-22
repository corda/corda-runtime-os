package net.corda.crypto.core

import net.corda.v5.cipher.suite.DigestAlgorithmFactory

/**
 * A simple interface to register and unregister a single custom digest provider.
 */
interface DigestAlgorithmFactoryProviderRegistry {
    /**
     * Register a function that returns a map of algorithm names to their factories.
     *
     * If this method is called twice, the previously registered function call is overwritten.
     */
    fun register(factoryProvider: () -> Map<String, DigestAlgorithmFactory>)

    /**
     * Unregister the current factory provider function.
     *
     * We want to implement to this to release any object references when OSGi unloads bundles.
     */
    fun unregister()
}
