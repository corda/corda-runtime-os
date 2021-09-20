package net.corda.crypto.impl

import net.corda.crypto.CryptoCategories
import net.corda.crypto.CryptoLibraryFactory
import net.corda.crypto.FreshKeySigningService
import net.corda.crypto.SigningService
import net.corda.crypto.impl.dev.DevCryptoService
import net.corda.crypto.impl.dev.DevCryptoServiceConfiguration
import net.corda.crypto.impl.dev.DevCryptoServiceProvider
import net.corda.crypto.impl.lifecycle.closeGracefully
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.CipherSuiteFactory
import net.corda.v5.cipher.suite.CryptoServiceContext
import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.v5.crypto.DigestService
import net.corda.v5.crypto.SignatureVerificationService
import org.osgi.service.component.annotations.Activate
import java.util.concurrent.ConcurrentHashMap

class CryptoLibraryFactoryDevImpl @Activate constructor(
    private val memberId: String,
    private val cipherSuiteFactory: CipherSuiteFactory
) : CryptoLibraryFactory, AutoCloseable {

    private val schemeMetadata = cipherSuiteFactory.getSchemeMap()

    private val devCryptoServiceProvider: DevCryptoServiceProvider by lazy(LazyThreadSafetyMode.PUBLICATION) {
        DevCryptoServiceProvider()
    }

    private val cryptoServices = ConcurrentHashMap<String, DevCryptoService>()

    override fun getSignatureVerificationService(): SignatureVerificationService =
        cipherSuiteFactory.getSignatureVerificationService()

    override fun getKeyEncodingService(): KeyEncodingService =
        cipherSuiteFactory.getSchemeMap()

    override fun getCipherSchemeMetadata(): CipherSchemeMetadata =
        cipherSuiteFactory.getSchemeMap()

    override fun getDigestService(): DigestService =
        cipherSuiteFactory.getDigestService()

    override fun getFreshKeySigningService(): FreshKeySigningService {
        val cryptoService = getCryptoService(memberId, CryptoCategories.LEDGER)
        val freshKeysCryptoService = getCryptoService(memberId, CryptoCategories.FRESH_KEYS)
        return FreshKeySigningServiceImpl(
            cache = cryptoService.signingCache,
            cryptoService = cryptoService,
            freshKeysCryptoService = freshKeysCryptoService,
            schemeMetadata = schemeMetadata,
            defaultFreshKeySignatureSchemeCodeName = DevCryptoService.SUPPORTED_SCHEME_CODE_NAME
        )
    }

    override fun getSigningService(category: String): SigningService {
        val cryptoService = getCryptoService(memberId, category)
        return SigningServiceImpl(
            cache = cryptoService.signingCache,
            cryptoService = cryptoService,
            schemeMetadata = schemeMetadata,
            defaultSignatureSchemeCodeName = DevCryptoService.SUPPORTED_SCHEME_CODE_NAME
        )
    }

    private fun getCryptoService(memberId: String, category: String): DevCryptoService =
        cryptoServices.getOrPut("$memberId:$category") {
            devCryptoServiceProvider.getInstance(
                CryptoServiceContext(
                    sandboxId = memberId,
                    category = category,
                    cipherSuiteFactory = cipherSuiteFactory,
                    config = DevCryptoServiceConfiguration()
                )
            ) as DevCryptoService
        }

    override fun close() {
        cryptoServices.clear()
        devCryptoServiceProvider.closeGracefully()
    }
}