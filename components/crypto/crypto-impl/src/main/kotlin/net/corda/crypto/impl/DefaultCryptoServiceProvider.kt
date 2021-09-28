package net.corda.crypto.impl

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
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@Component(service = [CryptoServiceProvider::class, DefaultCryptoServiceProvider::class])
open class DefaultCryptoServiceProvider @Activate constructor(
    @Reference(service = PersistentCacheFactory::class)
    private val persistenceFactories: List<PersistentCacheFactory>
) : Lifecycle, CryptoLifecycleComponent, CryptoServiceProvider<DefaultCryptoServiceConfig> {
    companion object {
        private val logger: Logger = contextLogger()
    }

    private val lock = ReentrantLock()

    override val name: String = CryptoServiceConfig.DEFAULT_SERVICE_NAME

    override val configType: Class<DefaultCryptoServiceConfig> = DefaultCryptoServiceConfig::class.java

    private var libraryConfig: CryptoLibraryConfig? = null

    private val isConfigured: Boolean
        get() = libraryConfig != null

    override var isRunning: Boolean = false

    override fun start() = lock.withLock {
        logger.info("Starting...")
        isRunning = true
    }

    override fun stop() = lock.withLock {
        logger.info("Stopping...")
        libraryConfig = null
        isRunning = false
    }

    override fun handleConfigEvent(config: CryptoLibraryConfig) = lock.withLock {
        logger.info("Received new configuration...")
        libraryConfig = config
    }

    override fun getInstance(context: CryptoServiceContext<DefaultCryptoServiceConfig>): CryptoService =
        lock.withLock {
            logger.info(
                "Creating instance of the {} for member {} and category",
                DefaultCryptoService::class.java.name,
                context.sandboxId,
                context.category
            )
            if (!isConfigured) {
                throw IllegalStateException("The factory haven't been started.")
            }
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
            it.name == libraryConfig!!.keyCache.cacheFactoryName
        } ?: throw CryptoServiceLibraryException(
            "Cannot find ${libraryConfig!!.keyCache.cacheFactoryName}",
            isRecoverable = false
        )
        return DefaultCryptoKeyCacheImpl(
            memberId = context.sandboxId,
            passphrase = context.config.passphrase,
            salt = context.config.salt,
            persistence = persistenceFactory.createDefaultCryptoPersistentCache(libraryConfig!!.keyCache),
            schemeMetadata = schemeMetadata
        )
    }
}