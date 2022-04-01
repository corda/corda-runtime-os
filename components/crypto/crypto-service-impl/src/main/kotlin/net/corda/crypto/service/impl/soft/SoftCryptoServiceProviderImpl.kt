package net.corda.crypto.service.impl.soft

import net.corda.crypto.persistence.SoftCryptoKeyCacheProvider
import net.corda.crypto.service.SoftCryptoServiceConfig
import net.corda.crypto.service.SoftCryptoServiceProvider
import net.corda.crypto.service.impl.AbstractComponent
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.CryptoService
import net.corda.v5.cipher.suite.CryptoServiceProvider
import net.corda.v5.crypto.DigestService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger

@Component(service = [CryptoServiceProvider::class, SoftCryptoServiceProvider::class])
open class SoftCryptoServiceProviderImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = CipherSchemeMetadata::class)
    private val schemeMetadata: CipherSchemeMetadata,
    @Reference(service = DigestService::class)
    private val digestService: DigestService,
    @Reference(service = SoftCryptoKeyCacheProvider::class)
    private val cacheProvider: SoftCryptoKeyCacheProvider
) : AbstractComponent<SoftCryptoServiceProviderImpl.Impl>(
    coordinatorFactory,
    LifecycleCoordinatorName.forComponent<SoftCryptoServiceProvider>(),
    InactiveImpl(),
    setOf(LifecycleCoordinatorName.forComponent<SoftCryptoKeyCacheProvider>())
), SoftCryptoServiceProvider {
    companion object {
        const val SERVICE_NAME = "soft"
        private val logger: Logger = contextLogger()
    }

    interface Impl : AutoCloseable {
        fun getInstance(config: SoftCryptoServiceConfig): CryptoService
        override fun close() = Unit
    }

    override fun createActiveImpl(): Impl = ActiveImpl(schemeMetadata, digestService, cacheProvider)

    override fun createInactiveImpl(): Impl = InactiveImpl()

    override val name: String = SERVICE_NAME

    override val configType: Class<SoftCryptoServiceConfig> = SoftCryptoServiceConfig::class.java

    override fun getInstance(config: SoftCryptoServiceConfig): CryptoService =
        impl.getInstance(config)

    private class InactiveImpl : Impl {
        override fun getInstance(config: SoftCryptoServiceConfig): CryptoService =
            throw IllegalStateException("The component is in invalid state")
    }

    private class ActiveImpl(
        private val schemeMetadata: CipherSchemeMetadata,
        private val digestService: DigestService,
        private val cacheProvider: SoftCryptoKeyCacheProvider
    ) : Impl {
        override fun getInstance(config: SoftCryptoServiceConfig): CryptoService {
            logger.info("Creating instance of the {}", SoftCryptoService::class.java.name)
            return SoftCryptoService(
                cache = cacheProvider.getInstance(
                    passphrase = config.passphrase,
                    salt = config.salt
                ),
                schemeMetadata = schemeMetadata,
                digestService = digestService
            )
        }
    }
}