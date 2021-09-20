package net.corda.crypto.testkit

import net.corda.crypto.impl.CipherSchemeMetadataProviderImpl
import net.corda.crypto.impl.config.CryptoCacheConfig
import net.corda.crypto.impl.dev.InMemoryPersistentCache
import net.corda.crypto.impl.dev.InMemoryPersistentCacheFactory
import net.corda.crypto.impl.persistence.DefaultCryptoCachedKeyInfo
import net.corda.crypto.impl.persistence.DefaultCryptoPersistentKeyInfo
import net.corda.crypto.impl.persistence.SigningPersistentKeyInfo
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.schemes.ECDSA_SECP256R1_CODE_NAME
import net.corda.v5.cipher.suite.schemes.SignatureScheme

class CryptoMocks(
    schemeMetadataOverride: CipherSchemeMetadata? = null
) {
    val persistentCacheFactory: InMemoryPersistentCacheFactory = InMemoryPersistentCacheFactory()

    val schemeMetadata: CipherSchemeMetadata = schemeMetadataOverride ?: CipherSchemeMetadataProviderImpl().getInstance()

    val signingPersistentKeyCache: InMemoryPersistentCache<SigningPersistentKeyInfo, SigningPersistentKeyInfo> =
        persistentCacheFactory.createSigningPersistentCache(
            CryptoCacheConfig.default
        ) as InMemoryPersistentCache<SigningPersistentKeyInfo, SigningPersistentKeyInfo>

    val defaultPersistentKeyCache: InMemoryPersistentCache<DefaultCryptoCachedKeyInfo, DefaultCryptoPersistentKeyInfo> =
        persistentCacheFactory.createDefaultCryptoPersistentCache(
            CryptoCacheConfig.default
        ) as InMemoryPersistentCache<DefaultCryptoCachedKeyInfo, DefaultCryptoPersistentKeyInfo>

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