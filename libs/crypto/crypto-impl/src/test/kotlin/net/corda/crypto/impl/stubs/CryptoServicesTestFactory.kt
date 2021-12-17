package net.corda.crypto.impl.stubs

import net.corda.crypto.SigningService
import net.corda.crypto.impl.CipherSchemeMetadataProviderImpl
import net.corda.crypto.impl.DefaultCryptoService
import net.corda.crypto.impl.DigestServiceProviderImpl
import net.corda.crypto.impl.FreshKeySigningServiceImpl
import net.corda.crypto.impl.SignatureVerificationServiceImpl
import net.corda.crypto.impl.SigningServiceImpl
import net.corda.crypto.impl.dev.InMemoryKeyValuePersistenceFactory
import net.corda.crypto.impl.persistence.DefaultCryptoKeyCache
import net.corda.crypto.impl.persistence.DefaultCryptoKeyCacheImpl
import net.corda.crypto.impl.persistence.SigningKeyCacheImpl
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.CipherSuiteFactory
import net.corda.v5.cipher.suite.CryptoService
import net.corda.v5.cipher.suite.schemes.SignatureScheme
import net.corda.v5.crypto.DigestService
import net.corda.v5.crypto.SignatureVerificationService
import java.util.UUID

class CryptoServicesTestFactory(
    schemeMetadataOverride: CipherSchemeMetadata? = null
) : CipherSuiteFactory {
    val wrappingKeyAlias = "wrapping-key-alias"

    val passphrase = "PASSPHRASE"

    val salt = "SALT"

    val memberId: String = UUID.randomUUID().toString()

    val persistenceFactory = InMemoryKeyValuePersistenceFactory()

    val schemeMetadata: CipherSchemeMetadata =
        schemeMetadataOverride ?: CipherSchemeMetadataProviderImpl().getInstance()

    val digest: DigestService by lazy {
        DigestServiceProviderImpl(null).getInstance(this)
    }

    val verifier: SignatureVerificationService by lazy {
        SignatureVerificationServiceImpl(schemeMetadata, digest)
    }

    val cryptoServiceCache: DefaultCryptoKeyCache = DefaultCryptoKeyCacheImpl(
        memberId = memberId,
        passphrase = passphrase,
        salt = salt,
        schemeMetadata = schemeMetadata,
        persistenceFactory = persistenceFactory
    )

    val signingKeyCache = SigningKeyCacheImpl(
        memberId = memberId,
        keyEncoder = schemeMetadata,
        persistenceFactory = persistenceFactory
    )

    val cryptoService = DefaultCryptoService(
        cache = cryptoServiceCache,
        schemeMetadata = schemeMetadata,
        hashingService = digest
    ).also { it.createWrappingKey(wrappingKeyAlias, true) }

    override fun getDigestService(): DigestService = digest

    override fun getSchemeMap(): CipherSchemeMetadata = schemeMetadata

    override fun getSignatureVerificationService(): SignatureVerificationService = verifier

    fun createSigningService(signatureScheme: SignatureScheme): SigningService =
        SigningServiceImpl(
            cache = signingKeyCache,
            cryptoService = cryptoService,
            defaultSignatureSchemeCodeName = signatureScheme.codeName,
            schemeMetadata = schemeMetadata
        )

    fun createFreshKeyService(
        signatureScheme: SignatureScheme,
        effectiveWrappingKeyAlias: String = wrappingKeyAlias
    ): FreshKeySigningService =
        FreshKeySigningServiceImpl(
            cache = signingKeyCache,
            cryptoService = cryptoService,
            freshKeysCryptoService = cryptoService,
            schemeMetadata = schemeMetadata,
            defaultFreshKeySignatureSchemeCodeName = signatureScheme.codeName,
            masterWrappingKeyAlias = effectiveWrappingKeyAlias
        )

    fun createCryptoServiceWithRandomMemberId(): CryptoService {
        val cache = DefaultCryptoKeyCacheImpl(
            memberId = UUID.randomUUID().toString(),
            passphrase = passphrase,
            salt = salt,
            schemeMetadata = schemeMetadata,
            persistenceFactory = persistenceFactory
        )
        return DefaultCryptoService(
            cache = cache,
            schemeMetadata = schemeMetadata,
            hashingService = digest
        )
    }

    fun createSigningServiceWithRandomMemberId(signatureScheme: SignatureScheme): SigningService {
        val cache = SigningKeyCacheImpl(
            memberId = UUID.randomUUID().toString(),
            keyEncoder = schemeMetadata,
            persistenceFactory = persistenceFactory
        )
        return SigningServiceImpl(
            cache = cache,
            cryptoService = cryptoService,
            defaultSignatureSchemeCodeName = signatureScheme.codeName,
            schemeMetadata = schemeMetadata
        )
    }

    fun createFreshKeyServiceWithRandomMemberId(
        signatureScheme: SignatureScheme,
        effectiveWrappingKeyAlias: String = wrappingKeyAlias
    ): FreshKeySigningService {
        val cache = SigningKeyCacheImpl(
            memberId = UUID.randomUUID().toString(),
            keyEncoder = schemeMetadata,
            persistenceFactory = persistenceFactory
        )
        return FreshKeySigningServiceImpl(
            cache = cache,
            cryptoService = cryptoService,
            freshKeysCryptoService = cryptoService,
            schemeMetadata = schemeMetadata,
            defaultFreshKeySignatureSchemeCodeName = signatureScheme.codeName,
            masterWrappingKeyAlias = effectiveWrappingKeyAlias
        )
    }
}