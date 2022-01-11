package net.corda.crypto.impl.stubs

import net.corda.crypto.CryptoConsts
import net.corda.crypto.SigningService
import net.corda.crypto.impl.CipherSchemeMetadataFactory
import net.corda.crypto.impl.CryptoServiceConfiguredInstance
import net.corda.crypto.impl.CryptoServiceFactory
import net.corda.crypto.impl.DigestServiceImpl
import net.corda.crypto.impl.DoubleSHA256DigestFactory
import net.corda.crypto.impl.soft.SoftCryptoService
import net.corda.crypto.impl.SignatureVerificationServiceImpl
import net.corda.crypto.impl.SigningServiceImpl
import net.corda.crypto.impl.dev.InMemoryKeyValuePersistenceFactory
import net.corda.crypto.impl.persistence.SoftCryptoKeyCache
import net.corda.crypto.impl.persistence.SoftCryptoKeyCacheImpl
import net.corda.crypto.impl.persistence.SigningKeyCacheImpl
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.CipherSuiteFactory
import net.corda.v5.cipher.suite.schemes.SignatureScheme
import net.corda.v5.crypto.DigestService
import net.corda.v5.crypto.SignatureVerificationService
import java.util.UUID

class CryptoServicesTestFactory(
    schemeMetadataOverride: CipherSchemeMetadata? = null
) : CipherSuiteFactory {
    val schemeMetadata: CipherSchemeMetadata =
        schemeMetadataOverride ?: CipherSchemeMetadataFactory().getInstance()

    val digest: DigestService by lazy {
        DigestServiceImpl(schemeMetadata, listOf(DoubleSHA256DigestFactory()), null)
    }

    val verifier: SignatureVerificationService by lazy {
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
        val schemeMetadata: CipherSchemeMetadata,
        val digest: DigestService
    ) {
        val wrappingKeyAlias = "wrapping-key-alias"

        private val passphrase = "PASSPHRASE"

        private val salt = "SALT"

        val persistenceFactory = InMemoryKeyValuePersistenceFactory()

        val cryptoServiceCache: SoftCryptoKeyCache = SoftCryptoKeyCacheImpl(
            tenantId = tenantId,
            passphrase = passphrase,
            salt = salt,
            schemeMetadata = schemeMetadata,
            persistenceFactory = persistenceFactory
        )

        val signingKeyCache = SigningKeyCacheImpl(
            tenantId = tenantId,
            keyEncoder = schemeMetadata,
            persistenceFactory = persistenceFactory
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
    }
}