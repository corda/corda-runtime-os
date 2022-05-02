package net.corda.crypto.service.impl.hsm.service

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.component.impl.AbstractConfigurableComponent
import net.corda.crypto.impl.config.toCryptoConfig
import net.corda.crypto.persistence.HSMCacheProvider
import net.corda.crypto.persistence.HSMTenantAssociation
import net.corda.crypto.service.HSMService
import net.corda.data.crypto.wire.hsm.HSMConfig
import net.corda.data.crypto.wire.hsm.HSMInfo
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.schema.configuration.ConfigKeys
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [HSMService::class])
class HSMServiceComponent @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    configurationReadService: ConfigurationReadService,
    @Reference(service = HSMCacheProvider::class)
    private val cacheProvider: HSMCacheProvider
) : AbstractConfigurableComponent<HSMServiceComponent.Impl>(
    coordinatorFactory = coordinatorFactory,
    myName = LifecycleCoordinatorName.forComponent<HSMService>(),
    configurationReadService = configurationReadService,
    impl = InactiveImpl(),
    dependencies = setOf(
        LifecycleCoordinatorName.forComponent<HSMCacheProvider>(),
        LifecycleCoordinatorName.forComponent<ConfigurationReadService>()
    ),
    configKeys = setOf(
        ConfigKeys.MESSAGING_CONFIG,
        ConfigKeys.BOOT_CONFIG,
        ConfigKeys.CRYPTO_CONFIG
    )
), HSMService {
    interface Impl : AutoCloseable {
        val service: HSMServiceImpl
    }

    override fun createActiveImpl(event: ConfigChangedEvent): Impl =
        ActiveImpl(event, cacheProvider)

    override fun createInactiveImpl(): Impl =
        InactiveImpl()

    override fun assignHSM(tenantId: String, category: String): HSMInfo =
        impl.service.assignHSM(tenantId, category)

    override fun assignSoftHSM(tenantId: String, category: String): HSMInfo =
        impl.service.assignSoftHSM(tenantId, category)

    override fun findAssignedHSM(tenantId: String, category: String): HSMInfo? =
        impl.service.findAssignedHSM(tenantId, category)

    override fun getPrivateTenantAssociation(tenantId: String, category: String): HSMTenantAssociation =
        impl.service.getPrivateTenantAssociation(tenantId, category)

    override fun putHSMConfig(config: HSMConfig) =
        impl.service.putHSMConfig(config)

    override fun lookup(): List<HSMInfo> =
        impl.service.lookup()

    class InactiveImpl: Impl {
        override val service: HSMServiceImpl
            get() = throw IllegalStateException("Component is in illegal state.")
        override fun close() = Unit
    }

    class ActiveImpl(
        event: ConfigChangedEvent,
        cacheProvider: HSMCacheProvider
    ): Impl {
        override val service: HSMServiceImpl = HSMServiceImpl(
            event.config.toCryptoConfig(),
            cacheProvider.getInstance()
        )

        override fun close() = service.close()
    }
}