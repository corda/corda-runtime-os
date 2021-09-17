package net.corda.components.crypto.services

import net.corda.crypto.impl.lifecycle.NewCryptoConfigReceived
import net.corda.crypto.impl.config.CryptoLibraryConfig
import net.corda.components.crypto.services.persistence.DefaultCryptoKeyCache
import net.corda.components.crypto.services.persistence.DefaultCryptoKeyCacheImpl
import net.corda.components.crypto.services.persistence.PersistentCacheFactory
import net.corda.crypto.impl.lifecycle.CryptoLifecycleComponent
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.CryptoService
import net.corda.v5.cipher.suite.CryptoServiceContext
import net.corda.v5.cipher.suite.CryptoServiceProvider
import net.corda.v5.cipher.suite.config.CryptoServiceConfig
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@Component(service = [CryptoServiceProvider::class, DefaultCryptoServiceProvider::class])
class DefaultCryptoServiceProvider @Activate constructor(
    @Reference(service = PersistentCacheFactory::class)
    private val persistenceFactory: PersistentCacheFactory
) : CryptoLifecycleComponent, CryptoServiceProvider<DefaultCryptoServiceConfig> {
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

    override fun handleConfigEvent(event: NewCryptoConfigReceived) = lock.withLock {
        logger.info("Received new configuration...")
        libraryConfig = event.config
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
}