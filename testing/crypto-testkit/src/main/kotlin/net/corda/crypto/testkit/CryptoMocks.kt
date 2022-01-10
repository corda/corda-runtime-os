package net.corda.crypto.testkit

import net.corda.crypto.impl.CipherSchemeMetadataProviderImpl
import net.corda.v5.cipher.suite.CipherSchemeMetadata

class CryptoMocks(
    schemeMetadataOverride: CipherSchemeMetadata? = null
) {
    val schemeMetadata: CipherSchemeMetadata =
        schemeMetadataOverride ?: CipherSchemeMetadataProviderImpl().getInstance()

    /*
    val persistenceFactoryProvider = InMemoryKeyValuePersistenceFactoryProvider()
    val persistenceFactory: KeyValuePersistenceFactory = persistenceFactoryProvider.get()

    val factories = Factories(this)

    class Factories(
        private val mocks: CryptoMocks,
        defaultSignatureSchemeCodeName: String = ECDSA_SECP256R1_CODE_NAME,
        defaultFreshKeySignatureSchemeCodeName: String = ECDSA_SECP256R1_CODE_NAME,
    ) {
        val defaultSignatureScheme: SignatureScheme =
            mocks.schemeMetadata.findSignatureScheme(defaultSignatureSchemeCodeName)

        val defaultFreshKeySignatureScheme: SignatureScheme =
            mocks.schemeMetadata.findSignatureScheme(defaultFreshKeySignatureSchemeCodeName)

        val cipherSuite = MockCipherSuiteFactory(mocks)

        val cryptoServices = MockCryptoFactory(mocks, defaultSignatureScheme, defaultFreshKeySignatureScheme)

        val cryptoLibrary = MockCryptoLibraryFactory(mocks)

        fun cryptoClients(memberId: String): MockCryptoLibraryClientsFactory =
            MockCryptoLibraryClientsFactory(mocks, memberId)
    }
     */
}