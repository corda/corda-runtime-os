package net.corda.components.crypto.services

import net.corda.crypto.impl.lifecycle.CryptoServiceLifecycleEventHandler
import net.corda.crypto.impl.lifecycle.CryptoLifecycleComponent
import net.corda.crypto.impl.config.CryptoConfigEvent
import net.corda.crypto.impl.config.CryptoLibraryConfig
import net.corda.components.crypto.services.persistence.DefaultCryptoKeyCache
import net.corda.components.crypto.services.persistence.DefaultCryptoKeyCacheImpl
import net.corda.components.crypto.services.persistence.PersistentCacheFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.StopEvent
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.CryptoService
import net.corda.v5.cipher.suite.CryptoServiceContext
import net.corda.v5.cipher.suite.CryptoServiceProvider
import net.corda.v5.cipher.suite.config.CryptoServiceConfig
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import kotlin.concurrent.withLock

@Component(service = [CryptoServiceProvider::class, DefaultCryptoServiceProvider::class])
class DefaultCryptoServiceProvider @Activate constructor(
    @Reference(service = CryptoServiceLifecycleEventHandler::class)
    private val cryptoServiceLifecycleEventHandler: CryptoServiceLifecycleEventHandler,
    @Reference(service = PersistentCacheFactory::class)
    private val persistenceFactory: PersistentCacheFactory
) : CryptoLifecycleComponent(cryptoServiceLifecycleEventHandler), CryptoServiceProvider<DefaultCryptoServiceConfig> {
    override val name: String = CryptoServiceConfig.DEFAULT_SERVICE_NAME

    override val configType: Class<DefaultCryptoServiceConfig> = DefaultCryptoServiceConfig::class.java

    private var libraryConfig: CryptoLibraryConfig? = null

    private val isConfigured: Boolean
        get() = libraryConfig != null

    override fun stop() = lock.withLock {
        libraryConfig = null
        super.stop()
    }

    override fun getInstance(context: CryptoServiceContext<DefaultCryptoServiceConfig>): CryptoService = lock.withLock {
        if(!isConfigured) {
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
        return DefaultCryptoKeyCacheImpl(
            memberId = context.sandboxId,
            passphrase = context.config.passphrase,
            salt = context.config.salt,
            persistence = persistenceFactory.createDefaultCryptoPersistentCache(libraryConfig!!.keyCache),
            schemeMetadata = schemeMetadata
        )
    }

    override fun handleLifecycleEvent(event: LifecycleEvent) = lock.withLock {
        logger.info("LifecycleEvent received: $event")
        when (event) {
            is CryptoConfigEvent -> {
                logger.info("Received config event {}", event::class.qualifiedName)
                reset(event.config)
            }
            is StopEvent -> {
                logger.info("Received stop event")
                stop()
            }
        }
    }

    private fun reset(config: CryptoLibraryConfig) {
        libraryConfig = config
    }
}