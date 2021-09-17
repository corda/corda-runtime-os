package net.corda.crypto

interface CryptoLibraryFactoryProvider {
    fun create(requestingComponent: String): CryptoLibraryFactory
}