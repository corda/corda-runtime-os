package net.corda.crypto.testkit

import net.corda.crypto.impl.DefaultCryptoService
import net.corda.crypto.impl.FreshKeySigningServiceImpl
import net.corda.crypto.impl.SigningServiceImpl
import net.corda.crypto.impl.persistence.DefaultCryptoKeyCacheImpl
import net.corda.crypto.impl.persistence.SigningKeyCacheImpl
import net.corda.crypto.CryptoConsts
import net.corda.crypto.SigningService
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.CryptoService
import net.corda.v5.cipher.suite.schemes.SignatureScheme
import java.util.concurrent.ConcurrentHashMap

class MockCryptoFactory(
    private val mocks: CryptoMocks,
    private val defaultSignatureScheme: SignatureScheme,
    private val defaultFreshKeySignatureScheme: SignatureScheme
) {
    val signingPersistence = ConcurrentHashMap<String, SigningKeyCacheImpl>()
    private val freshKeyServices = ConcurrentHashMap<String, FreshKeySigningService>()
    private val signingServices = ConcurrentHashMap<String, SigningService>()
    private val cryptoServices = ConcurrentHashMap<String, CryptoService>()

    val cipherSchemeMetadata: CipherSchemeMetadata
        get() = mocks.schemeMetadata

    fun getFreshKeySigningService(memberId: String): FreshKeySigningService {
        val cache = getSigningKeyCache(memberId)
        return freshKeyServices.getOrPut(memberId) {
            FreshKeySigningServiceImpl(
                cache = cache,
                cryptoService = getCryptoService(memberId, CryptoConsts.CryptoCategories.LEDGER),
                freshKeysCryptoService = getCryptoService(memberId, CryptoConsts.CryptoCategories.FRESH_KEYS),
                schemeMetadata = mocks.schemeMetadata,
                defaultFreshKeySignatureSchemeCodeName = defaultFreshKeySignatureScheme.codeName
            )
        }
    }

    fun getSigningService(memberId: String, category: String): SigningService {
        val cache = getSigningKeyCache(memberId)
        return signingServices.getOrPut("$memberId:$category") {
            SigningServiceImpl(
                cache = cache,
                cryptoService = getCryptoService(memberId, category),
                schemeMetadata = mocks.schemeMetadata,
                defaultSignatureSchemeCodeName = defaultSignatureScheme.codeName
            )
        }
    }

    private fun getCryptoService(memberId: String, category: String): CryptoService =
        cryptoServices.getOrPut("$memberId:$category") {
            DefaultCryptoService(
                DefaultCryptoKeyCacheImpl(
                    memberId = memberId,
                    passphrase = "PASSPHRASE",
                    salt = "SALT",
                    schemeMetadata = mocks.schemeMetadata,
                    persistenceFactory = mocks.persistenceFactory
                ),
                schemeMetadata = mocks.schemeMetadata,
                hashingService = mocks.factories.cipherSuite.getDigestService()
            )
        }

    private fun getSigningKeyCache(memberId: String): SigningKeyCacheImpl =
        signingPersistence.getOrPut(memberId) {
            SigningKeyCacheImpl(
                memberId = memberId,
                keyEncoder = mocks.schemeMetadata,
                persistenceFactory = mocks.persistenceFactory
            )
        }
}
