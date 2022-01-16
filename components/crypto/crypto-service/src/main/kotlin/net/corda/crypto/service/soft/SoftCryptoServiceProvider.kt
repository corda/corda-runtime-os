package net.corda.crypto.service.soft

import net.corda.crypto.component.persistence.SoftPersistenceProvider
import net.corda.crypto.impl.config.CryptoPersistenceConfig
import net.corda.crypto.impl.config.softCryptoService
import net.corda.crypto.service.persistence.SoftCryptoKeyCache
import net.corda.crypto.service.persistence.SoftCryptoKeyCacheImpl
import net.corda.lifecycle.Lifecycle
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.CryptoService
import net.corda.v5.cipher.suite.CryptoServiceContext
import net.corda.v5.cipher.suite.CryptoServiceProvider
import net.corda.v5.cipher.suite.config.CryptoLibraryConfig
import net.corda.v5.cipher.suite.config.CryptoServiceConfig
import net.corda.v5.cipher.suite.lifecycle.CryptoLifecycleComponent
import net.corda.v5.crypto.exceptions.CryptoServiceException
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger

@Component(service = [CryptoServiceProvider::class, SoftCryptoServiceProvider::class])
open class SoftCryptoServiceProvider :
    Lifecycle,
    CryptoLifecycleComponent,
    CryptoServiceProvider<SoftCryptoServiceConfig> {
    companion object {
        private val logger: Logger = contextLogger()
    }

    @Volatile
    @Reference(service = SoftPersistenceProvider::class)
    lateinit var persistenceFactory: SoftPersistenceProvider

    private var impl: Impl? = null

    override val name: String = CryptoServiceConfig.DEFAULT_SERVICE_NAME

    override val configType: Class<SoftCryptoServiceConfig> = SoftCryptoServiceConfig::class.java

    override var isRunning: Boolean = false

    override fun start() {
        logger.info("Starting...")
        isRunning = true
    }

    override fun stop() {
        logger.info("Stopping...")
        isRunning = false
    }

    override fun handleConfigEvent(config: CryptoLibraryConfig) {
        logger.info("Received new configuration...")
        impl = Impl(
            persistenceFactory,
            config.softCryptoService,
            logger
        )
    }

    override fun getInstance(context: CryptoServiceContext<SoftCryptoServiceConfig>): CryptoService =
        impl?.getInstance(context)
            ?: throw CryptoServiceException("Provider haven't been initialised yet.", true)

    private class Impl(
        private val persistenceFactory: SoftPersistenceProvider,
        private val config: CryptoPersistenceConfig,
        private val logger: Logger
    ) {
        fun getInstance(context: CryptoServiceContext<SoftCryptoServiceConfig>): CryptoService {
            logger.info(
                "Creating instance of the {} for member {} and category",
                SoftCryptoService::class.java.name,
                context.memberId,
                context.category
            )
            val schemeMetadata = context.cipherSuiteFactory.getSchemeMap()
            return SoftCryptoService(
                cache = makeCache(context, schemeMetadata),
                schemeMetadata = schemeMetadata,
                hashingService = context.cipherSuiteFactory.getDigestService()
            )
        }

        private fun makeCache(
            context: CryptoServiceContext<SoftCryptoServiceConfig>,
            schemeMetadata: CipherSchemeMetadata
        ): SoftCryptoKeyCache {
            return SoftCryptoKeyCacheImpl(
                tenantId = context.memberId,
                passphrase = context.config.passphrase,
                salt = context.config.salt,
                schemeMetadata = schemeMetadata,
                persistenceFactory = persistenceFactory
            )
        }
    }
}