package net.corda.crypto

interface CryptoLibraryClientsFactoryProvider : AutoCloseable {
    fun create(requestingComponent: String): CryptoLibraryClientsFactory
}