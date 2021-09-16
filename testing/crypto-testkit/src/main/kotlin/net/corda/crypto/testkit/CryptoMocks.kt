package net.corda.crypto.testkit

import com.typesafe.config.ConfigFactory
import net.corda.cipher.suite.impl.CipherSchemeMetadataProviderImpl
import net.corda.cipher.suite.impl.config.CryptoCacheConfig
import net.corda.components.crypto.services.persistence.DefaultCryptoCachedKeyInfo
import net.corda.components.crypto.services.persistence.DefaultCryptoPersistentKeyInfo
import net.corda.components.crypto.services.persistence.SigningPersistentKeyInfo
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.schemes.ECDSA_SECP256R1_CODE_NAME
import net.corda.v5.cipher.suite.schemes.SignatureScheme

class CryptoMocks(
    schemeMetadataOverride: CipherSchemeMetadata? = null
) {
    private val persistentCacheFactory: MockPersistentCacheFactory = MockPersistentCacheFactory()

    val schemeMetadata: CipherSchemeMetadata = schemeMetadataOverride ?: CipherSchemeMetadataProviderImpl().getInstance()

    val signingPersistentKeyCache: MockPersistentCache<SigningPersistentKeyInfo, SigningPersistentKeyInfo> =
        persistentCacheFactory.createSigningPersistentCache(
            CryptoCacheConfig(ConfigFactory.empty())
        ) as MockPersistentCache<SigningPersistentKeyInfo, SigningPersistentKeyInfo>

    val defaultPersistentKeyCache: MockPersistentCache<DefaultCryptoCachedKeyInfo, DefaultCryptoPersistentKeyInfo> =
        persistentCacheFactory.createDefaultCryptoPersistentCache(
            CryptoCacheConfig(ConfigFactory.empty())
        ) as MockPersistentCache<DefaultCryptoCachedKeyInfo, DefaultCryptoPersistentKeyInfo>

    val factories = Factories(this)

    fun factories(
        defaultSignatureSchemeCodeName: String,
        defaultFreshKeySignatureSchemeCodeName: String) =
        Factories(this, defaultSignatureSchemeCodeName, defaultFreshKeySignatureSchemeCodeName)

    class Factories(
        mocks: CryptoMocks,
        defaultSignatureSchemeCodeName: String = ECDSA_SECP256R1_CODE_NAME,
        defaultFreshKeySignatureSchemeCodeName: String = ECDSA_SECP256R1_CODE_NAME,
    ) {
        val defaultSignatureScheme: SignatureScheme =
            mocks.schemeMetadata.findSignatureScheme(defaultSignatureSchemeCodeName)

        val defaultFreshKeySignatureScheme: SignatureScheme =
            mocks.schemeMetadata.findSignatureScheme(defaultFreshKeySignatureSchemeCodeName)

        val cipherSuite = MockCipherSuiteFactory(mocks)

        val cryptoServices = MockCryptoFactory(mocks, defaultSignatureScheme, defaultFreshKeySignatureScheme)

        val cryptoClients = MockCryptoLibraryFactory(mocks)
    }
}