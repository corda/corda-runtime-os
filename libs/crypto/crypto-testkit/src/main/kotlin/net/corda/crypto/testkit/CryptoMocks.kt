package net.corda.crypto.testkit

import net.corda.cipher.suite.impl.CipherSchemeMetadataProviderImpl
import net.corda.cipher.suite.impl.DefaultCachedKey
import net.corda.cipher.suite.impl.DefaultCryptoPersistentKey
import net.corda.cipher.suite.impl.DigestServiceProviderImpl
import net.corda.cipher.suite.impl.dev.InMemorySigningServicePersistentCache
import net.corda.cipher.suite.impl.dev.InMemorySimplePersistentCache
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.schemes.DigestScheme
import net.corda.v5.cipher.suite.schemes.EDDSA_ED25519_CODE_NAME
import net.corda.v5.cipher.suite.schemes.SignatureScheme
import net.corda.v5.crypto.DigestService
import java.util.UUID

class CryptoMocks(
    val sandboxId: String = UUID.randomUUID().toString(),
    val defaultSignatureSchemeCodeName: String = EDDSA_ED25519_CODE_NAME,
    val defaultFreshKeySignatureSchemeCodeName: String = EDDSA_ED25519_CODE_NAME,
    private val schemeMetadataImpl: CipherSchemeMetadata? = null
) {
    private val schemeMetadata: MockCipherSchemeMetadata by lazy {
        MockCipherSchemeMetadata(this, schemeMetadataImpl ?: CipherSchemeMetadataProviderImpl().getInstance())
    }

    val signingPersistentKeyCache: InMemorySigningServicePersistentCache = InMemorySigningServicePersistentCache()
    val signingKeyCache: MockSigningKeyCache = MockSigningKeyCache(this, signingPersistentKeyCache)
    val defaultPersistentKeyCache: InMemorySimplePersistentCache<DefaultCachedKey, DefaultCryptoPersistentKey> =
        InMemorySimplePersistentCache()
    val basicKeyCache: MockDefaultKeyCache = MockDefaultKeyCache(this, defaultPersistentKeyCache)
    val schemes: Array<SignatureScheme> get() = schemeMetadata.schemes
    val digests: Array<DigestScheme> get() = schemeMetadata.digests

    val defaultSignatureScheme: SignatureScheme = schemeMetadata.findSignatureScheme(defaultSignatureSchemeCodeName)
    val defaultFreshKeySignatureScheme: SignatureScheme = schemeMetadata.findSignatureScheme(defaultFreshKeySignatureSchemeCodeName)

    fun cryptoLibraryFactory(): MockCryptoLibraryFactory = MockCryptoLibraryFactory(this)

    fun cipherSuiteFactory(): MockCipherSuiteFactory = MockCipherSuiteFactory(this)

    fun signingService(defaultSchemeCodeName: String? = null): MockSigningService = MockSigningService(
        mocks = this,
        defaultSignatureSchemeCodeName = defaultSchemeCodeName ?: defaultSignatureSchemeCodeName
    )

    fun freshKeySigningService(
        freshKeysDefaultSchemeCodeName: String? = null
    ): MockFreshKeySigningService = MockFreshKeySigningService(
        mocks = this,
        defaultFreshKeySignatureSchemeCodeName = freshKeysDefaultSchemeCodeName ?: defaultFreshKeySignatureSchemeCodeName
    )

    fun signatureVerificationService(): MockSignatureVerificationService = MockSignatureVerificationService(this)

    fun schemeMetadata(): MockCipherSchemeMetadata = schemeMetadata

    fun cryptoService(): MockCryptoService = MockCryptoService(this, basicKeyCache)

    fun digestService(): DigestService = DigestServiceProviderImpl().getInstance(cipherSuiteFactory())
}