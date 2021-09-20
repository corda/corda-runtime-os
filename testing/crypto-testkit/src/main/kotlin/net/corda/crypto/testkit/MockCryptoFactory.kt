package net.corda.crypto.testkit

import net.corda.components.crypto.CryptoFactory
import net.corda.crypto.impl.DefaultCryptoService
import net.corda.crypto.impl.FreshKeySigningServiceImpl
import net.corda.crypto.impl.SigningServiceImpl
import net.corda.crypto.impl.persistence.DefaultCryptoKeyCacheImpl
import net.corda.crypto.impl.persistence.SigningKeyCacheImpl
import net.corda.crypto.CryptoCategories
import net.corda.crypto.FreshKeySigningService
import net.corda.crypto.SigningService
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.CryptoService
import net.corda.v5.cipher.suite.schemes.SignatureScheme
import java.util.concurrent.ConcurrentHashMap

class MockCryptoFactory(
    private val mocks: CryptoMocks,
    private val defaultSignatureScheme: SignatureScheme,
    private val defaultFreshKeySignatureScheme: SignatureScheme
) : CryptoFactory {
    private val freshKeyServices = ConcurrentHashMap<String, FreshKeySigningService>()
    private val signingServices = ConcurrentHashMap<String, SigningService>()
    private val cryptoServices = ConcurrentHashMap<String, CryptoService>()

    override val cipherSchemeMetadata: CipherSchemeMetadata
        get() = mocks.schemeMetadata

    override fun getFreshKeySigningService(memberId: String): FreshKeySigningService =
        freshKeyServices.getOrPut(memberId) {
            FreshKeySigningServiceImpl(
                cache = SigningKeyCacheImpl(
                    memberId = memberId,
                    keyEncoder = mocks.schemeMetadata,
                    persistence = mocks.signingPersistentKeyCache
                ),
                cryptoService = getCryptoService(memberId, CryptoCategories.LEDGER),
                freshKeysCryptoService = getCryptoService(memberId, CryptoCategories.FRESH_KEYS),
                schemeMetadata = mocks.schemeMetadata,
                defaultFreshKeySignatureSchemeCodeName = defaultFreshKeySignatureScheme.codeName
            )
        }

    override fun getSigningService(memberId: String, category: String): SigningService =
        signingServices.getOrPut("$memberId:$category") {
            SigningServiceImpl(
                cache = SigningKeyCacheImpl(
                    memberId = memberId,
                    keyEncoder = mocks.schemeMetadata,
                    persistence = mocks.signingPersistentKeyCache
                ),
                cryptoService = getCryptoService(memberId, category),
                schemeMetadata = mocks.schemeMetadata,
                defaultSignatureSchemeCodeName = defaultSignatureScheme.codeName
            )
        }

    private fun getCryptoService(memberId: String, category: String): CryptoService =
        cryptoServices.getOrPut("$memberId:$category") {
            DefaultCryptoService(
                DefaultCryptoKeyCacheImpl(
                    memberId = memberId,
                    passphrase = "PASSPHRASE",
                    salt = "SALT",
                    schemeMetadata = mocks.schemeMetadata,
                    persistence = mocks.defaultPersistentKeyCache
                ),
                schemeMetadata = mocks.schemeMetadata,
                hashingService = mocks.factories.cipherSuite.getDigestService()
            )
        }
}
