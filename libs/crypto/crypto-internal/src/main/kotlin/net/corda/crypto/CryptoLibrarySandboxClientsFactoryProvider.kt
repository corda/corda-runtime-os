package net.corda.crypto

/**
 * Provides operations to create [CryptoLibraryClientsFactory] for sandboxed (state machine, flows) environments where
 * the current member id have to inferred from the ambient context.
 */
interface CryptoLibrarySandboxClientsFactoryProvider : AutoCloseable {
    /**
     * Gets an instance of [CryptoLibraryClientsFactory]
     */
    fun get(requestingComponent: String): CryptoLibraryClientsFactory
}