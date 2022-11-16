package net.corda.processors.db.internal.reconcile.db

import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.KeyValuePairList
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.schema.CordaDb
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.membership.datamodel.GroupParametersEntity
import net.corda.membership.lib.GroupParametersFactory
import net.corda.orm.JpaEntitiesRegistry
import net.corda.processors.db.internal.reconcile.db.ReconciliationContext.VirtualNodeReconciliationContext
import net.corda.reconciliation.VersionedRecord
import net.corda.utilities.VisibleForTesting
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.v5.membership.GroupParameters
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import java.util.stream.Stream
import javax.persistence.EntityManager

/**
 * Reconciler for handling reconciliation between each vnode vault database on the cluster
 * and the compacted kafka topic for the group parameters.
 */
@Suppress("LongParameterList")
class GroupParametersReconciler(
    cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    private val dbConnectionManager: DbConnectionManager,
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
    private val jpaEntitiesRegistry: JpaEntitiesRegistry,
    private val groupParametersFactory: GroupParametersFactory
) : ReconcilerWrapper {
    private companion object {
        val logger = contextLogger()
        val dependencies = setOf(
            LifecycleCoordinatorName.forComponent<DbConnectionManager>(),
            LifecycleCoordinatorName.forComponent<VirtualNodeInfoReadService>()
        )
        const val FAILED_DESERIALIZATION = "Could not deserialize group parameters from the database entity."
    }

    private val cordaAvroDeserializer = cordaAvroSerializationFactory.createAvroDeserializer(
        { logger.warn(FAILED_DESERIALIZATION) },
        KeyValuePairList::class.java
    )

    private val entitiesSet
        get() = jpaEntitiesRegistry.get(CordaDb.Vault.persistenceUnitName)
            ?: throw CordaRuntimeException(
                "persistenceUnitName '${CordaDb.Vault.persistenceUnitName}' is not registered."
            )

    @VisibleForTesting
    internal var dbReconciler: DbReconcilerReader<HoldingIdentity, GroupParameters>? = null

    override fun close() {
        dbReconciler?.stop()
    }

    override fun updateInterval(intervalMillis: Long) {
        logger.debug { "Group parameters reconciliation interval set to $intervalMillis ms" }

        if (dbReconciler == null) {
            dbReconciler = DbReconcilerReader(
                coordinatorFactory,
                HoldingIdentity::class.java,
                GroupParameters::class.java,
                dependencies,
                reconciliationContextFactory,
                ::getVersionRecords,
                onStreamClose = ::onStreamClose
            ).also {
                it.start()
            }
        }
    }

    private val reconciliationContextFactory = {
        virtualNodeInfoReadService.getAll().map {
            VirtualNodeReconciliationContext(
                dbConnectionManager.createEntityManagerFactory(
                    it.vaultDmlConnectionId,
                    entitiesSet
                ),
                it.holdingIdentity
            )
        }
    }

    /**
     * Close the previously created EntityManagerFactory.
     */
    private fun onStreamClose(reconciliationContext: ReconciliationContext) = reconciliationContext.emf.close()


    /**
     * Retrieve the group parameters [VersionedRecord] for a specific virtual node.
     */
    private fun getVersionRecords(
        em: EntityManager,
        reconciliationContext: ReconciliationContext
    ): Stream<VersionedRecord<HoldingIdentity, GroupParameters>> {
        require(reconciliationContext is VirtualNodeReconciliationContext) {
            "Reconciliation information must be virtual node level for group parameters reconciliation"
        }
        val criteriaBuilder = em.criteriaBuilder
        val criteriaQuery = criteriaBuilder.createQuery(GroupParametersEntity::class.java)

        val root = criteriaQuery.from(GroupParametersEntity::class.java)
        val query = criteriaQuery.select(root)
            .orderBy(criteriaBuilder.desc(root.get<String>("epoch")))

        val entity = em.createQuery(query)
            .setMaxResults(1)
            .singleResult

        val deserializedParams = cordaAvroDeserializer.deserialize(entity.parameters)
            ?: throw CordaRuntimeException(FAILED_DESERIALIZATION)

        return Stream.of(
            object : VersionedRecord<HoldingIdentity, GroupParameters> {
                override val version = entity.epoch
                override val isDeleted = false
                override val key = reconciliationContext.holdingIdentity
                override val value = groupParametersFactory.create(deserializedParams)
            }
        )
    }
}
