package net.corda.crypto

/**
 * Provides operations to create [CryptoLibraryClientsFactory] for sandboxed (state machine, flows) environments where
 * the current member id have to inferred from the ambient context.
 */
interface CryptoLibrarySandboxClientsFactoryProvider : AutoCloseable {
    /**
     * Creates an instance of [CryptoLibraryClientsFactory]
     */
    fun create(requestingComponent: String): CryptoLibraryClientsFactory
}