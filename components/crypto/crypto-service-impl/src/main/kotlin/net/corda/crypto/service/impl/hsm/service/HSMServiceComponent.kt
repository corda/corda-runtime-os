package net.corda.crypto.service.impl.hsm.service

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.client.CryptoOpsProxyClient
import net.corda.crypto.component.impl.AbstractConfigurableComponent
import net.corda.crypto.impl.config.toCryptoConfig
import net.corda.crypto.persistence.hsm.HSMCacheProvider
import net.corda.crypto.persistence.hsm.HSMConfig
import net.corda.crypto.persistence.hsm.HSMTenantAssociation
import net.corda.crypto.service.HSMService
import net.corda.data.crypto.wire.hsm.HSMCategoryInfo
import net.corda.data.crypto.wire.hsm.HSMInfo
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.schema.configuration.ConfigKeys
import net.corda.v5.cipher.suite.CipherSchemeMetadata
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
    private val cacheProvider: HSMCacheProvider,
    @Reference(service = CipherSchemeMetadata::class)
    private val schemeMetadata: CipherSchemeMetadata,
    @Reference(service = CryptoOpsProxyClient::class)
    private val opsProxyClient: CryptoOpsProxyClient
) : AbstractConfigurableComponent<HSMServiceComponent.Impl>(
    coordinatorFactory = coordinatorFactory,
    myName = LifecycleCoordinatorName.forComponent<HSMService>(),
    configurationReadService = configurationReadService,
    impl = InactiveImpl(),
    dependencies = setOf(
        LifecycleCoordinatorName.forComponent<HSMCacheProvider>(),
        LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
        LifecycleCoordinatorName.forComponent<CryptoOpsClient>()
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
        ActiveImpl(event, cacheProvider, schemeMetadata, opsProxyClient)

    override fun createInactiveImpl(): Impl =
        InactiveImpl()

    override fun putHSMConfig(info: HSMInfo, serviceConfig: ByteArray) =
        impl.service.putHSMConfig(info, serviceConfig)

    override fun assignHSM(tenantId: String, category: String, context: Map<String, String>): HSMInfo =
        impl.service.assignHSM(tenantId, category, context)

    override fun assignSoftHSM(tenantId: String, category: String): HSMInfo =
        impl.service.assignSoftHSM(tenantId, category)

    override fun linkCategories(configId: String, links: List<HSMCategoryInfo>) =
        impl.service.linkCategories(configId, links)

    override fun getLinkedCategories(configId: String): List<HSMCategoryInfo> =
        impl.service.getLinkedCategories(configId)

    override fun lookup(filter: Map<String, String>): List<HSMInfo> =
        impl.service.lookup(filter)

    override fun findAssignedHSM(tenantId: String, category: String): HSMTenantAssociation? =
        impl.service.findAssignedHSM(tenantId, category)

    override fun findHSMConfig(configId: String): HSMConfig? =
        impl.service.findHSMConfig(configId)

    class InactiveImpl: Impl {
        override val service: HSMServiceImpl
            get() = throw IllegalStateException("Component is in illegal state.")
        override fun close() = Unit
    }

    class ActiveImpl(
        event: ConfigChangedEvent,
        cacheProvider: HSMCacheProvider,
        schemeMetadata: CipherSchemeMetadata,
        opsProxyClient: CryptoOpsProxyClient
    ): Impl {
        override val service: HSMServiceImpl = HSMServiceImpl(
            event.config.toCryptoConfig(),
            cacheProvider.getInstance(),
            schemeMetadata,
            opsProxyClient
        )

        override fun close() = service.close()
    }
}