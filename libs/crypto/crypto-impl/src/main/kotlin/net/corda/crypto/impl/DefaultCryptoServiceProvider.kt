package net.corda.crypto.impl

import net.corda.crypto.impl.config.CryptoCacheConfig
import net.corda.crypto.impl.config.keyCache
import net.corda.crypto.impl.persistence.DefaultCryptoKeyCache
import net.corda.crypto.impl.persistence.DefaultCryptoKeyCacheImpl
import net.corda.crypto.impl.persistence.PersistentCacheFactory
import net.corda.lifecycle.Lifecycle
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.CryptoService
import net.corda.v5.cipher.suite.CryptoServiceContext
import net.corda.v5.cipher.suite.CryptoServiceProvider
import net.corda.v5.cipher.suite.config.CryptoLibraryConfig
import net.corda.v5.cipher.suite.config.CryptoServiceConfig
import net.corda.v5.cipher.suite.lifecycle.CryptoLifecycleComponent
import net.corda.v5.crypto.exceptions.CryptoServiceLibraryException
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger

@Component(service = [CryptoServiceProvider::class, DefaultCryptoServiceProvider::class])
open class DefaultCryptoServiceProvider @Activate constructor(
    @Reference(service = PersistentCacheFactory::class)
    private val persistenceFactories: List<PersistentCacheFactory>
) : Lifecycle, CryptoLifecycleComponent, CryptoServiceProvider<DefaultCryptoServiceConfig> {
    companion object {
        private val logger: Logger = contextLogger()
    }

    private var impl = Impl(
        persistenceFactories,
        CryptoCacheConfig.default,
        logger
    )

    override val name: String = CryptoServiceConfig.DEFAULT_SERVICE_NAME

    override val configType: Class<DefaultCryptoServiceConfig> = DefaultCryptoServiceConfig::class.java

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
            persistenceFactories,
            config.keyCache,
            logger
        )
    }

    override fun getInstance(context: CryptoServiceContext<DefaultCryptoServiceConfig>): CryptoService =
        impl.getInstance(context)

    private class Impl(
        private val persistenceFactories: List<PersistentCacheFactory>,
        private val config: CryptoCacheConfig,
        private val logger: Logger
    ) {
        fun getInstance(context: CryptoServiceContext<DefaultCryptoServiceConfig>): CryptoService {
            logger.info(
                "Creating instance of the {} for member {} and category",
                DefaultCryptoService::class.java.name,
                context.sandboxId,
                context.category
            )
            val schemeMetadata = context.cipherSuiteFactory.getSchemeMap()
            return DefaultCryptoService(
                cache = makeCache(context, schemeMetadata),
                schemeMetadata = schemeMetadata,
                hashingService = context.cipherSuiteFactory.getDigestService()
            )
        }

        private fun makeCache(
            context: CryptoServiceContext<DefaultCryptoServiceConfig>,
            schemeMetadata: CipherSchemeMetadata
        ): DefaultCryptoKeyCache {
            val persistenceFactory = persistenceFactories.firstOrNull {
                it.name == config.cacheFactoryName
            } ?: throw CryptoServiceLibraryException(
                "Cannot find ${config.cacheFactoryName}",
                isRecoverable = false
            )
            return DefaultCryptoKeyCacheImpl(
                memberId = context.sandboxId,
                passphrase = context.config.passphrase,
                salt = context.config.salt,
                persistence = persistenceFactory.createDefaultCryptoPersistentCache(config),
                schemeMetadata = schemeMetadata
            )
        }
    }
}