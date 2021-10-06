package net.corda.crypto

interface CryptoLibraryClientsFactoryProvider : AutoCloseable {
    fun create(
        memberId: String,
        requestingComponent: String
    ): CryptoLibraryClientsFactory
}