package net.corda.crypto.service.impl.signing

import net.corda.crypto.CryptoConsts
import net.corda.crypto.impl.components.CipherSchemeMetadataImpl
import net.corda.crypto.impl.components.DigestServiceImpl
import net.corda.crypto.impl.components.SignatureVerificationServiceImpl
import net.corda.crypto.service.impl.persistence.SigningKeyCacheImpl
import net.corda.crypto.service.CryptoServiceConfiguredInstance
import net.corda.crypto.service.CryptoServiceFactory
import net.corda.crypto.service.SigningService
import net.corda.crypto.service.impl.TestSigningKeysPersistenceProvider
import net.corda.crypto.service.impl.TestSoftKeysPersistenceProvider
import net.corda.crypto.service.impl.persistence.SoftCryptoKeyCache
import net.corda.crypto.service.impl.persistence.SoftCryptoKeyCacheImpl
import net.corda.crypto.service.impl.soft.SoftCryptoService
import net.corda.data.crypto.persistence.SigningKeysRecord
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.schemes.SignatureScheme
import net.corda.v5.crypto.DigestService
import net.corda.v5.crypto.SignatureVerificationService
import java.security.KeyPair
import java.security.PublicKey
import java.util.UUID

class CryptoServicesTestFactory {
    val schemeMetadata: CipherSchemeMetadata = CipherSchemeMetadataImpl()

    val digest: DigestService by lazy {
        DigestServiceImpl(schemeMetadata, null)
    }

    val verifier: SignatureVerificationService by lazy {
        SignatureVerificationServiceImpl(schemeMetadata, digest)
    }

    fun createCryptoServices(
        tenantId: String = UUID.randomUUID().toString(),
        category: String = CryptoConsts.Categories.LEDGER
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

        private val signingPersistenceFactory = TestSigningKeysPersistenceProvider()

        private val softPersistenceFactory = TestSoftKeysPersistenceProvider()

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

        fun getSigningKeyRecord(publicKey: PublicKey): SigningKeysRecord? {
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