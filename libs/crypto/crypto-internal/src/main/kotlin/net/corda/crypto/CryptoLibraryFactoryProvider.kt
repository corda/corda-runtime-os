package net.corda.crypto

interface CryptoLibraryFactoryProvider {
    fun create(memberId: String, requestingComponent: String): CryptoLibraryFactory
}