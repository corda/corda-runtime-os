package net.corda.crypto.client

import net.corda.crypto.CryptoCategories
import net.corda.crypto.CryptoLibraryClientsFactory
import net.corda.crypto.FreshKeySigningService
import net.corda.crypto.SigningService
import net.corda.crypto.impl.FreshKeySigningServiceImpl
import net.corda.crypto.impl.SigningServiceImpl
import net.corda.crypto.impl.closeGracefully
import net.corda.crypto.impl.dev.DevCryptoService
import net.corda.crypto.impl.dev.DevCryptoServiceConfiguration
import net.corda.crypto.impl.dev.DevCryptoServiceProvider
import net.corda.crypto.impl.dev.InMemoryKeyValuePersistenceFactoryProvider
import net.corda.v5.cipher.suite.CipherSuiteFactory
import net.corda.v5.cipher.suite.CryptoServiceContext
import java.util.concurrent.ConcurrentHashMap

class CryptoLibraryClientsFactoryDevImpl(
    private val memberId: String,
    private val cipherSuiteFactory: CipherSuiteFactory
) : CryptoLibraryClientsFactory, AutoCloseable {

    private val schemeMetadata = cipherSuiteFactory.getSchemeMap()

    private val devCryptoServiceProvider: DevCryptoServiceProvider by lazy(LazyThreadSafetyMode.PUBLICATION) {
        DevCryptoServiceProvider(
            listOf(InMemoryKeyValuePersistenceFactoryProvider())
        )
    }

    private val cryptoServices = ConcurrentHashMap<String, DevCryptoService>()

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
                    memberId = memberId,
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