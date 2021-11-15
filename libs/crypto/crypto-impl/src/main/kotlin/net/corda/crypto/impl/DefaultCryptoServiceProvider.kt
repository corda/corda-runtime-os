package net.corda.crypto.impl

import net.corda.crypto.impl.config.CryptoPersistenceConfig
import net.corda.crypto.impl.config.defaultCryptoService
import net.corda.crypto.impl.persistence.DefaultCryptoKeyCache
import net.corda.crypto.impl.persistence.DefaultCryptoKeyCacheImpl
import net.corda.crypto.impl.persistence.KeyValuePersistenceFactoryProvider
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
import org.osgi.service.component.annotations.ReferenceCardinality
import org.osgi.service.component.annotations.ReferencePolicyOption
import org.slf4j.Logger

@Component(service = [CryptoServiceProvider::class, DefaultCryptoServiceProvider::class])
open class DefaultCryptoServiceProvider @Activate constructor(
    @Reference(
        service = KeyValuePersistenceFactoryProvider::class,
        cardinality = ReferenceCardinality.AT_LEAST_ONE,
        policyOption = ReferencePolicyOption.GREEDY
    )
    private val persistenceProviders: List<KeyValuePersistenceFactoryProvider>
) : Lifecycle, CryptoLifecycleComponent, CryptoServiceProvider<DefaultCryptoServiceConfig> {
    companion object {
        private val logger: Logger = contextLogger()
    }

    private var impl = Impl(
        persistenceProviders,
        CryptoPersistenceConfig.default,
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
            persistenceProviders,
            config.defaultCryptoService,
            logger
        )
    }

    override fun getInstance(context: CryptoServiceContext<DefaultCryptoServiceConfig>): CryptoService =
        impl.getInstance(context)

    private class Impl(
        private val persistenceProviders: List<KeyValuePersistenceFactoryProvider>,
        private val config: CryptoPersistenceConfig,
        private val logger: Logger
    ) {
        fun getInstance(context: CryptoServiceContext<DefaultCryptoServiceConfig>): CryptoService {
            logger.info(
                "Creating instance of the {} for member {} and category",
                DefaultCryptoService::class.java.name,
                context.memberId,
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
            val persistenceFactory = persistenceProviders.firstOrNull {
                it.name == config.factoryName
            }?.get() ?: throw CryptoServiceLibraryException(
                "Cannot find ${config.factoryName}",
                isRecoverable = false
            )
            return DefaultCryptoKeyCacheImpl(
                memberId = context.memberId,
                passphrase = context.config.passphrase,
                salt = context.config.salt,
                schemeMetadata = schemeMetadata,
                persistenceFactory = persistenceFactory
            )
        }
    }
}