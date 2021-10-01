package net.corda.crypto.impl.stubs

import net.corda.crypto.CryptoCategories
import net.corda.crypto.FreshKeySigningService
import net.corda.crypto.SigningService
import net.corda.crypto.impl.CipherSchemeMetadataProviderImpl
import net.corda.crypto.impl.DefaultCryptoService
import net.corda.crypto.impl.DigestServiceProviderImpl
import net.corda.crypto.impl.FreshKeySigningServiceImpl
import net.corda.crypto.impl.SignatureVerificationServiceImpl
import net.corda.crypto.impl.SigningServiceImpl
import net.corda.crypto.impl.config.CryptoCacheConfig
import net.corda.crypto.impl.dev.InMemoryPersistentCache
import net.corda.crypto.impl.dev.InMemoryPersistentCacheFactory
import net.corda.crypto.impl.persistence.DefaultCryptoCachedKeyInfo
import net.corda.crypto.impl.persistence.DefaultCryptoKeyCacheImpl
import net.corda.crypto.impl.persistence.DefaultCryptoPersistentKeyInfo
import net.corda.crypto.impl.persistence.SigningKeyCacheImpl
import net.corda.crypto.impl.persistence.SigningPersistentKeyInfo
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.CipherSuiteFactory
import net.corda.v5.cipher.suite.CryptoService
import net.corda.v5.cipher.suite.schemes.ECDSA_SECP256R1_CODE_NAME
import net.corda.v5.cipher.suite.schemes.SignatureScheme
import net.corda.v5.crypto.DigestService
import net.corda.v5.crypto.SignatureVerificationService
import java.util.concurrent.ConcurrentHashMap

class MockCryptoFactory(
    defaultSignatureSchemeCodeName: String = ECDSA_SECP256R1_CODE_NAME,
    defaultFreshKeySignatureSchemeCodeName: String = ECDSA_SECP256R1_CODE_NAME,
    schemeMetadataOverride: CipherSchemeMetadata? = null
) {
    val schemeMetadata: CipherSchemeMetadata =
        schemeMetadataOverride ?: CipherSchemeMetadataProviderImpl().getInstance()

    val cipherSuiteFactory: CipherSuiteFactory = object : CipherSuiteFactory {
        override fun getDigestService(): DigestService =
            createDigestService()
        override fun getSchemeMap(): CipherSchemeMetadata =
            schemeMetadata
        override fun getSignatureVerificationService(): SignatureVerificationService =
            createVerificationService()
    }

    private val persistentCacheFactory: InMemoryPersistentCacheFactory = InMemoryPersistentCacheFactory()

    private val defaultSignatureScheme: SignatureScheme =
        schemeMetadata.findSignatureScheme(defaultSignatureSchemeCodeName)

    private val defaultFreshKeySignatureScheme: SignatureScheme =
        schemeMetadata.findSignatureScheme(defaultFreshKeySignatureSchemeCodeName)

    val signingPersistentKeyCache: InMemoryPersistentCache<SigningPersistentKeyInfo, SigningPersistentKeyInfo> =
        persistentCacheFactory.createSigningPersistentCache(
            CryptoCacheConfig.default
        ) as InMemoryPersistentCache<SigningPersistentKeyInfo, SigningPersistentKeyInfo>

    val defaultPersistentKeyCache: InMemoryPersistentCache<DefaultCryptoCachedKeyInfo, DefaultCryptoPersistentKeyInfo> =
        persistentCacheFactory.createDefaultCryptoPersistentCache(
            CryptoCacheConfig.default
        ) as InMemoryPersistentCache<DefaultCryptoCachedKeyInfo, DefaultCryptoPersistentKeyInfo>

    private val freshKeyServices = ConcurrentHashMap<String, FreshKeySigningService>()
    private val signingServices = ConcurrentHashMap<String, SigningService>()
    private val cryptoServices = ConcurrentHashMap<String, CryptoService>()

    fun createFreshKeySigningService(memberId: String): FreshKeySigningService =
        freshKeyServices.getOrPut(memberId) {
            FreshKeySigningServiceImpl(
                cache = SigningKeyCacheImpl(
                    memberId = memberId,
                    keyEncoder = schemeMetadata,
                    persistence = signingPersistentKeyCache
                ),
                cryptoService = createCryptoService(memberId, CryptoCategories.LEDGER),
                freshKeysCryptoService = createCryptoService(memberId, CryptoCategories.FRESH_KEYS),
                schemeMetadata = schemeMetadata,
                defaultFreshKeySignatureSchemeCodeName = defaultFreshKeySignatureScheme.codeName
            )
        }

    fun createSigningService(memberId: String, category: String): SigningService =
        signingServices.getOrPut("$memberId:$category") {
            SigningServiceImpl(
                cache = SigningKeyCacheImpl(
                    memberId = memberId,
                    keyEncoder = schemeMetadata,
                    persistence = signingPersistentKeyCache
                ),
                cryptoService = createCryptoService(memberId, category),
                schemeMetadata = schemeMetadata,
                defaultSignatureSchemeCodeName = defaultSignatureScheme.codeName
            )
        }

    fun createDigestService(): DigestService =
        DigestServiceProviderImpl().getInstance(cipherSuiteFactory)

    fun createVerificationService(): SignatureVerificationService =
        SignatureVerificationServiceImpl(schemeMetadata, createDigestService())

    private fun createCryptoService(memberId: String, category: String): CryptoService =
        cryptoServices.getOrPut("$memberId:$category") {
            DefaultCryptoService(
                DefaultCryptoKeyCacheImpl(
                    memberId = memberId,
                    passphrase = "PASSPHRASE",
                    salt = "SALT",
                    schemeMetadata = schemeMetadata,
                    persistence = defaultPersistentKeyCache
                ),
                schemeMetadata = schemeMetadata,
                hashingService = createDigestService()
            )
        }
}
