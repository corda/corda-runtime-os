package net.corda.crypto.persistence.impl

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.component.impl.AbstractConfigurableComponent
import net.corda.crypto.component.impl.DependenciesTracker
import net.corda.crypto.config.impl.MasterKeyPolicy
import net.corda.crypto.core.CryptoTenants
import net.corda.crypto.persistence.HSMStore
import net.corda.crypto.persistence.HSMUsage
import net.corda.crypto.persistence.getEntityManagerFactory
import net.corda.crypto.softhsm.HSMRepository
import net.corda.crypto.softhsm.impl.HSMRepositoryImpl
import net.corda.data.crypto.wire.hsm.HSMAssociationInfo
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.orm.JpaEntitiesRegistry
import net.corda.schema.configuration.ConfigKeys
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Suppress("LongParameterList")
@Component(service = [HSMStore::class])
class HSMStoreImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    configurationReadService: ConfigurationReadService,
    @Reference(service = DbConnectionManager::class)
    private val dbConnectionManager: DbConnectionManager,
    @Reference(service = JpaEntitiesRegistry::class)
    private val jpaEntitiesRegistry: JpaEntitiesRegistry,
    @Reference(service = VirtualNodeInfoReadService::class)
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
) : AbstractConfigurableComponent<HSMStoreImpl.Impl>(
    coordinatorFactory = coordinatorFactory,
    myName = LifecycleCoordinatorName.forComponent<HSMStore>(),
    configurationReadService = configurationReadService,
    upstream = DependenciesTracker.Default(
        setOf(
            LifecycleCoordinatorName.forComponent<DbConnectionManager>(),
            LifecycleCoordinatorName.forComponent<VirtualNodeInfoReadService>(),
            LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
        )
    ),
    configKeys = setOf(ConfigKeys.CRYPTO_CONFIG)
), HSMStore {

    override fun createActiveImpl(event: ConfigChangedEvent): Impl =
        Impl(dbConnectionManager, jpaEntitiesRegistry, virtualNodeInfoReadService)

    override fun findTenantAssociation(tenantId: String, category: String): HSMAssociationInfo? =
        impl.findTenantAssociation(tenantId, category)

    override fun getHSMUsage(): List<HSMUsage> = impl.getHSMUsage()

    override fun associate(
        tenantId: String,
        category: String,
        hsmId: String,
        masterKeyPolicy: MasterKeyPolicy,
    ): HSMAssociationInfo = impl.associate(tenantId, category, hsmId, masterKeyPolicy)

    class Impl(
        private val dbConnectionManager: DbConnectionManager,
        private val jpaEntitiesRegistry: JpaEntitiesRegistry,
        private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
    ) : DownstreamAlwaysUpAbstractImpl() {

        private fun openRepository(): HSMRepository = HSMRepositoryImpl(
                getEntityManagerFactory(
                    CryptoTenants.CRYPTO,
                    dbConnectionManager,
                    virtualNodeInfoReadService,
                    jpaEntitiesRegistry
                ),
                CryptoTenants.CRYPTO
            )

        fun findTenantAssociation(tenantId: String, category: String): HSMAssociationInfo? =
            openRepository().let {
                it.findTenantAssociation(tenantId, category)
            }

        fun getHSMUsage(): List<HSMUsage> = openRepository().let {
            it.getHSMUsage()
        }


        fun associate(
            tenantId: String,
            category: String,
            hsmId: String,
            masterKeyPolicy: MasterKeyPolicy,
        ): HSMAssociationInfo = openRepository().let {
            it.associate(tenantId, category, hsmId, masterKeyPolicy)
        }

        override fun close() {
            super.close()
        }
    }
}