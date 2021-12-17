package net.corda.crypto.testkit

import net.corda.crypto.CryptoLibraryClientsFactory
import net.corda.crypto.SigningService

class MockCryptoLibraryClientsFactory(
    private val mocks: CryptoMocks,
    val memberId: String
) : CryptoLibraryClientsFactory {
    override fun getFreshKeySigningService(): FreshKeySigningService =
        mocks.factories.cryptoServices.getFreshKeySigningService(memberId)

    override fun getSigningService(category: String): SigningService =
        mocks.factories.cryptoServices.getSigningService(memberId, category)
}