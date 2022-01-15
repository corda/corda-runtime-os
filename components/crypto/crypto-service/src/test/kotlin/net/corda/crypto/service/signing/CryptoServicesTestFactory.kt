package net.corda.crypto.service.signing

import net.corda.crypto.CryptoConsts
import net.corda.crypto.impl.CipherSchemeMetadataFactory
import net.corda.crypto.impl.DigestServiceImpl
import net.corda.crypto.impl.DoubleSHA256DigestFactory
import net.corda.crypto.impl.SignatureVerificationServiceImpl
import net.corda.crypto.service.persistence.SigningKeyCacheImpl
import net.corda.crypto.component.persistence.SigningKeyRecord
import net.corda.crypto.persistence.inmemory.SigningKeysInMemoryPersistenceProvider
import net.corda.crypto.persistence.inmemory.SoftInMemoryPersistenceProvider
import net.corda.crypto.service.persistence.SoftCryptoKeyCache
import net.corda.crypto.service.persistence.SoftCryptoKeyCacheImpl
import net.corda.crypto.service.soft.SoftCryptoService
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.CipherSuiteFactory
import net.corda.v5.cipher.suite.schemes.SignatureScheme
import net.corda.v5.crypto.DigestService
import net.corda.v5.crypto.SignatureVerificationService
import java.security.KeyPair
import java.security.PublicKey
import java.util.UUID

class CryptoServicesTestFactory(
    schemeMetadataOverride: CipherSchemeMetadata? = null
) : CipherSuiteFactory {
    private val schemeMetadata: CipherSchemeMetadata =
        schemeMetadataOverride ?: CipherSchemeMetadataFactory().getInstance()

    private val digest: DigestService by lazy {
        DigestServiceImpl(schemeMetadata, listOf(DoubleSHA256DigestFactory()), null)
    }

    private val verifier: SignatureVerificationService by lazy {
        SignatureVerificationServiceImpl(schemeMetadata, digest)
    }

    override fun getDigestService(): DigestService = digest

    override fun getSchemeMap(): CipherSchemeMetadata = schemeMetadata

    override fun getSignatureVerificationService(): SignatureVerificationService = verifier

    fun createCryptoServices(
        tenantId: String = UUID.randomUUID().toString(),
        category: String = CryptoConsts.CryptoCategories.LEDGER
    ): CryptoServices = CryptoServices(tenantId, category, schemeMetadata, digest)

    class CryptoServices(
        val tenantId: String,
        val category: String,
        private val schemeMetadata: CipherSchemeMetadata,
        digest: DigestService
    ) {
        val wrappingKeyAlias = "wrapping-key-alias"

        private val passphrase = "PASSPHRASE"

        private val salt = "SALT"

        private val signingPersistenceFactory = SigningKeysInMemoryPersistenceProvider()

        private val softPersistenceFactory = SoftInMemoryPersistenceProvider()

        private val cryptoServiceCache: SoftCryptoKeyCache = SoftCryptoKeyCacheImpl(
            tenantId = tenantId,
            passphrase = passphrase,
            salt = salt,
            schemeMetadata = schemeMetadata,
            persistenceFactory = softPersistenceFactory
        )

        private val signingKeyCache = SigningKeyCacheImpl(
            tenantId = tenantId,
            keyEncoder = schemeMetadata,
            persistenceFactory = signingPersistenceFactory
        )

        val cryptoService = SoftCryptoService(
            cache = cryptoServiceCache,
            schemeMetadata = schemeMetadata,
            hashingService = digest
        ).also { it.createWrappingKey(wrappingKeyAlias, true) }

        fun createSigningService(
            signatureScheme: SignatureScheme,
            effectiveWrappingKeyAlias: String = wrappingKeyAlias
        ): SigningService =
            SigningServiceImpl(
                tenantId = tenantId,
                cache = signingKeyCache,
                cryptoServiceFactory = object : CryptoServiceFactory {
                    override fun getInstance(tenantId: String, category: String): CryptoServiceConfiguredInstance =
                        CryptoServiceConfiguredInstance(
                            tenantId = tenantId,
                            category = category,
                            defaultSignatureScheme = signatureScheme,
                            wrappingKeyAlias = effectiveWrappingKeyAlias,
                            instance = cryptoService
                        )
                },
                schemeMetadata = schemeMetadata
            )

        fun getSigningKeyRecord(publicKey: PublicKey): SigningKeyRecord? {
            return signingKeyCache.find(publicKey)
        }

        fun getKeyPair(alias: String): KeyPair? {
            val record = cryptoServiceCache.find(alias)
            return if(record != null) {
                KeyPair(record.publicKey, record.privateKey)
            } else {
                null
            }
        }
    }
}