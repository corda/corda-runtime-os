package net.corda.uniqueness.backingstore.impl.osgi

import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.schema.CordaDb
import net.corda.ledger.libs.uniqueness.UniquenessSecureHashFactory
import net.corda.ledger.libs.uniqueness.backingstore.BackingStore
import net.corda.ledger.libs.uniqueness.backingstore.BackingStoreMetricsFactory
import net.corda.ledger.libs.uniqueness.backingstore.impl.JPABackingStoreEntities
import net.corda.ledger.libs.uniqueness.backingstore.impl.JPABackingStoreImpl
import net.corda.ledger.libs.uniqueness.data.UniquenessHoldingIdentity
import net.corda.libs.virtualnode.common.exception.VirtualNodeNotFoundException
import net.corda.orm.JpaEntitiesRegistry
import net.corda.orm.PersistenceExceptionCategorizer
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import javax.persistence.EntityManagerFactory

@Component(service = [BackingStore::class])
class JPABackingStoreOsgiImpl(delegate: BackingStore, jpaEntitiesRegistry: JpaEntitiesRegistry) : BackingStore by delegate {

    @Suppress("LongParameterList")
    @Activate constructor(
        @Reference(service = JpaEntitiesRegistry::class)
        jpaEntitiesRegistry: JpaEntitiesRegistry,
        @Reference(service = DbConnectionManager::class)
        dbConnectionManager: DbConnectionManager,
        @Reference(service = PersistenceExceptionCategorizer::class)
        persistenceExceptionCategorizer: PersistenceExceptionCategorizer,
        @Reference(service = VirtualNodeInfoReadService::class)
        virtualNodeInfoReadService: VirtualNodeInfoReadService,
        @Reference(service = BackingStoreMetricsFactory::class)
        backingStoreMetricsFactory: BackingStoreMetricsFactory,
        @Reference(service = UniquenessSecureHashFactory::class)
        uniquenessSecureHashFactory: UniquenessSecureHashFactory
    ) : this(
        JPABackingStoreImpl(
            getEntityManagerFactory = { getEntityManagerFactory(virtualNodeInfoReadService, dbConnectionManager, jpaEntitiesRegistry, it) },
            backingStoreMetricsFactory = backingStoreMetricsFactory,
            persistenceExceptionCategorizer = persistenceExceptionCategorizer,
            uniquenessSecureHashFactory = uniquenessSecureHashFactory
        ),
        jpaEntitiesRegistry
    )

    init {
        jpaEntitiesRegistry.register(
            CordaDb.Uniqueness.persistenceUnitName,
            JPABackingStoreEntities.classes
        )
    }

    private companion object {
        fun getEntityManagerFactory(
            virtualNodeInfoReadService: VirtualNodeInfoReadService,
            dbConnectionManager: DbConnectionManager,
            jpaEntitiesRegistry: JpaEntitiesRegistry,
            holdingIdentity: UniquenessHoldingIdentity
        ): EntityManagerFactory {
            val virtualNodeInfo = virtualNodeInfoReadService.getByHoldingIdentityShortHash(holdingIdentity.shortHash)
                ?: throw VirtualNodeNotFoundException("Virtual node ${holdingIdentity.shortHash} not found")
            val uniquenessDmlConnectionId = virtualNodeInfo.uniquenessDmlConnectionId
            requireNotNull(uniquenessDmlConnectionId) { "uniquenessDmlConnectionId is null" }

            return dbConnectionManager.getOrCreateEntityManagerFactory(
                uniquenessDmlConnectionId,
                entitiesSet = jpaEntitiesRegistry.get(CordaDb.Uniqueness.persistenceUnitName)
                    ?: throw IllegalStateException(
                        "persistenceUnitName " +
                                "${CordaDb.Uniqueness.persistenceUnitName} is not registered."
                    )
            )
        }
    }
}
