package net.corda.crypto

interface CryptoLibraryClientsFactoryProvider {
    fun create(requestingComponent: String): CryptoLibraryClientsFactory
}