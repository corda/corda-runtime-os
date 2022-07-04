package net.corda.crypto.service.impl.hsm.service

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.component.impl.AbstractConfigurableComponent
import net.corda.crypto.component.impl.DependenciesTracker
import net.corda.crypto.impl.config.toCryptoConfig
import net.corda.crypto.persistence.hsm.HSMConfig
import net.corda.crypto.persistence.hsm.HSMStoreProvider
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
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    configurationReadService: ConfigurationReadService,
    @Reference(service = HSMStoreProvider::class)
    private val storeProvider: HSMStoreProvider,
    @Reference(service = CipherSchemeMetadata::class)
    private val schemeMetadata: CipherSchemeMetadata,
    @Reference(service = CryptoOpsClient::class)
    private val cryptoOpsClient: CryptoOpsClient
) : AbstractConfigurableComponent<HSMServiceComponent.Impl>(
    coordinatorFactory = coordinatorFactory,
    myName = LifecycleCoordinatorName.forComponent<HSMService>(),
    configurationReadService = configurationReadService,
    upstream = DependenciesTracker.Default(
        setOf(
            LifecycleCoordinatorName.forComponent<HSMStoreProvider>(),
            LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
            LifecycleCoordinatorName.forComponent<CryptoOpsClient>()
        )
    ),
    configKeys = setOf(
        ConfigKeys.MESSAGING_CONFIG,
        ConfigKeys.BOOT_CONFIG,
        ConfigKeys.CRYPTO_CONFIG
    )
), HSMService {
    override fun createActiveImpl(event: ConfigChangedEvent): Impl =
        Impl(coordinatorFactory, event, storeProvider, schemeMetadata, cryptoOpsClient)

    override fun putHSMConfig(info: HSMInfo, serviceConfig: ByteArray) =
        impl.service.putHSMConfig(info, serviceConfig)

    override fun assignHSM(tenantId: String, category: String, context: Map<String, String>): HSMInfo =
        impl.service.assignHSM(tenantId, category, context)

    override fun assignSoftHSM(tenantId: String, category: String, context: Map<String, String>): HSMInfo =
        impl.service.assignSoftHSM(tenantId, category, context)

    override fun linkCategories(configId: String, links: List<HSMCategoryInfo>) =
        impl.service.linkCategories(configId, links)

    override fun getLinkedCategories(configId: String): List<HSMCategoryInfo> =
        impl.service.getLinkedCategories(configId)

    override fun lookup(filter: Map<String, String>): List<HSMInfo> =
        impl.service.lookup(filter)

    override fun findAssignedHSM(tenantId: String, category: String): HSMTenantAssociation? =
        impl.service.findAssignedHSM(tenantId, category)

    override fun findAssociation(associationId: String): HSMTenantAssociation? =
        impl.service.findAssociation(associationId)

    override fun findHSMConfig(configId: String): HSMConfig? =
        impl.service.findHSMConfig(configId)

    class Impl(
        coordinatorFactory: LifecycleCoordinatorFactory,
        event: ConfigChangedEvent,
        storeProvider: HSMStoreProvider,
        schemeMetadata: CipherSchemeMetadata,
        cryptoOpsClient: CryptoOpsClient
    ) : AbstractImpl {
        val service: HSMServiceImpl = HSMServiceImpl(
            event.config.toCryptoConfig(),
            storeProvider.getInstance(),
            schemeMetadata,
            cryptoOpsClient
        )

        private val _downstream = DependenciesTracker.AlwaysUp(
            coordinatorFactory,
            this
        ).also { it.start() }

        override val downstream: DependenciesTracker = _downstream

        override fun close() {
            _downstream.close()
            service.close()
        }
    }
}