package net.corda.crypto

interface CryptoLibrarySandboxClientsFactoryProvider : AutoCloseable {
    fun create(requestingComponent: String): CryptoLibraryClientsFactory
}