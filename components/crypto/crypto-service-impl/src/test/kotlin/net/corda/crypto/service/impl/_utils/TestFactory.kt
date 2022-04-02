package net.corda.crypto.service.impl._utils

import net.corda.crypto.impl.components.CipherSchemeMetadataImpl
import net.corda.crypto.impl.components.DigestServiceImpl
import net.corda.crypto.impl.components.SignatureVerificationServiceImpl
import net.corda.crypto.persistence.SigningCachedKey
import net.corda.crypto.service.CryptoServiceFactory
import net.corda.crypto.service.CryptoServiceRef
import net.corda.crypto.service.SigningService
import net.corda.crypto.service.impl.signing.SigningServiceImpl
import net.corda.crypto.service.impl.soft.SoftCryptoService
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.schemes.SignatureScheme
import net.corda.v5.crypto.DigestService
import net.corda.v5.crypto.SignatureVerificationService
import java.security.PublicKey

object TestFactory {
    val schemeMetadata: CipherSchemeMetadata = CipherSchemeMetadataImpl()

    private val digest: DigestService by lazy {
        DigestServiceImpl(schemeMetadata, null)
    }

    val verifier: SignatureVerificationService by lazy {
        SignatureVerificationServiceImpl(schemeMetadata, digest)
    }

    const val wrappingKeyAlias = "wrapping-key-alias"

    private const val passphrase = "PASSPHRASE"

    private const val salt = "SALT"

    private val signingCacheProvider = TestSigningKeyCacheProvider().also { it.start() }

    private val softCacheProvider = TestSoftCryptoKeyCacheProvider().also { it.start() }

    private val signingCache = signingCacheProvider.getInstance()

    val cryptoService = SoftCryptoService(
        cache = softCacheProvider.getInstance(passphrase, salt),
        schemeMetadata = schemeMetadata,
        digestService = digest
    ).also { it.createWrappingKey(wrappingKeyAlias, true, emptyMap()) }

    fun createSigningService(
        signatureScheme: SignatureScheme,
        effectiveWrappingKeyAlias: String = wrappingKeyAlias
    ): SigningService =
        SigningServiceImpl(
            cache = signingCache,
            cryptoServiceFactory = object : CryptoServiceFactory {
                override fun getInstance(tenantId: String, category: String): CryptoServiceRef {
                    check(isRunning) {
                        "The provider is in invalid state."
                    }
                    return CryptoServiceRef(
                        tenantId = tenantId,
                        category = category,
                        signatureScheme = signatureScheme,
                        masterKeyAlias = effectiveWrappingKeyAlias,
                        aliasSecret = null,
                        instance = cryptoService
                    )
                }

                override var isRunning: Boolean = false
                    private set

                override fun start() {
                    isRunning = true
                }

                override fun stop() {
                    isRunning = false
                }
            }.also { it.start() },
            schemeMetadata = schemeMetadata
        )

    fun getSigningCachedKey(tenantId: String, publicKey: PublicKey): SigningCachedKey? {
        return signingCache.act(tenantId) { it.find(publicKey) }
    }
}