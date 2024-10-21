package net.corda.uniqueness.backingstore.impl.osgi

import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.schema.CordaDb
import net.corda.ledger.libs.uniqueness.backingstore.BackingStore
import net.corda.ledger.libs.uniqueness.backingstore.BackingStoreMetricsFactory
import net.corda.ledger.libs.uniqueness.backingstore.impl.JPABackingStoreEntities
import net.corda.ledger.libs.uniqueness.backingstore.impl.JPABackingStoreBase
import net.corda.ledger.libs.uniqueness.data.UniquenessHoldingIdentity
import net.corda.libs.virtualnode.common.exception.VirtualNodeNotFoundException
import net.corda.orm.JpaEntitiesRegistry
import net.corda.orm.PersistenceExceptionCategorizer
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import javax.persistence.EntityManagerFactory

@Component(service = [BackingStore::class])
class JPABackingStoreOsgiImpl @Activate constructor(
    @Reference(service = JpaEntitiesRegistry::class)
    private val jpaEntitiesRegistry: JpaEntitiesRegistry,
    @Reference(service = DbConnectionManager::class)
    private val dbConnectionManager: DbConnectionManager,
    @Reference(service = PersistenceExceptionCategorizer::class)
    private val persistenceExceptionCategorizer: PersistenceExceptionCategorizer,
    @Reference(service = VirtualNodeInfoReadService::class)
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
    @Reference(service = BackingStoreMetricsFactory::class)
    private val backingStoreMetricsFactory: BackingStoreMetricsFactory
) : JPABackingStoreBase(backingStoreMetricsFactory, persistenceExceptionCategorizer) {

    init {
        jpaEntitiesRegistry.register(
            CordaDb.Uniqueness.persistenceUnitName,
            JPABackingStoreEntities.classes
        )
    }

    override fun getEntityManagerFactory(holdingIdentity: UniquenessHoldingIdentity): EntityManagerFactory {
        val virtualNodeInfo = virtualNodeInfoReadService.getByHoldingIdentityShortHash(holdingIdentity.shortHash) ?:
        throw VirtualNodeNotFoundException("Virtual node ${holdingIdentity.shortHash} not found")
        val uniquenessDmlConnectionId = virtualNodeInfo.uniquenessDmlConnectionId
        requireNotNull(uniquenessDmlConnectionId) {"uniquenessDmlConnectionId is null"}

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
