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
import net.corda.reconciliation.VersionedRecord
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.v5.membership.GroupParameters
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.stream.Stream
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory

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

    private var dbReconcilers =
        ConcurrentHashMap<HoldingIdentity, DbReconcilerReader<HoldingIdentity, GroupParameters>>()

    private var emfBucket = ConcurrentHashMap<String, EntityManagerFactory>()

    override fun close() {
        dbReconcilers.forEach { (_, reader) ->
            reader.stop()
        }
        dbReconcilers.clear()
    }

    var virtualNodeReadServiceCallback: AutoCloseable? = null

    override fun updateInterval(intervalMillis: Long) {
        logger.debug { "Group parameters reconciliation interval set to $intervalMillis ms" }

        // Build database reconciler readers for all virtual nodes which do not yet have a reconciler reader.
        virtualNodeInfoReadService.getAll().forEach(::buildVNodeReconcilerReader)

        // Register a callback so we can react to new virtual nodes being created.
        virtualNodeReadServiceCallback?.close()
        virtualNodeReadServiceCallback = virtualNodeInfoReadService.registerCallback { changedKeys, currentSnapshot ->
            changedKeys.forEach {
                val vnodeInfo = currentSnapshot[it]

                // If virtual node info was removed
                if (vnodeInfo == null) {
                    // Stop and remove any existing database reconcile reader
                    dbReconcilers.computeIfPresent(it) { _, oldValue ->
                        oldValue.stop()
                        null
                    }
                } else {
                    // build a database reconiler reader if one does not yet exist.
                    buildVNodeReconcilerReader(vnodeInfo)
                }
            }
        }
    }

    /**
     * Build a DBReconcileReader for a virtual node.
     * Only create if one does not already exist for the virtual node.
     */
    private fun buildVNodeReconcilerReader(vnodeInfo: VirtualNodeInfo) {
        dbReconcilers.computeIfAbsent(vnodeInfo.holdingIdentity) { holdingId ->
            val uniqueId = "${vnodeInfo.holdingIdentity.shortHash.value}-${UUID.randomUUID()}"

            /**
             * Create EntityManagerFactory and store it under a unique ID to be closed after use.
             */
            fun emfFactory() = dbConnectionManager.createEntityManagerFactory(
                vnodeInfo.vaultDmlConnectionId,
                entitiesSet
            ).also { emf ->
                emfBucket[uniqueId] = emf
            }

            /**
             * Close the previously created EntityManagerFactory.
             */
            fun emfTearDown() = emfBucket.computeIfPresent(uniqueId) { _, oldValue ->
                oldValue.close()
                null
            }

            /**
             * Query for the group parameters entity, using the vnode information to build the key.
             */
            fun getVersionedRecords(em: EntityManager) = getVersionRecordsForHoldingIdentity(em, holdingId)

            DbReconcilerReader(
                coordinatorFactory,
                HoldingIdentity::class.java,
                GroupParameters::class.java,
                dependencies,
                ::emfFactory,
                ::getVersionedRecords,
                onStreamClose = ::emfTearDown
            ).also {
                it.start()
            }
        }
    }

    /**
     * Retrieve the group parameters [VersionedRecord] for a specific virtual node.
     */
    private fun getVersionRecordsForHoldingIdentity(
        em: EntityManager,
        holdingId: HoldingIdentity
    ): Stream<VersionedRecord<HoldingIdentity, GroupParameters>> {
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
                override val key = holdingId
                override val value = groupParametersFactory.create(deserializedParams)
            }
        )
    }
}
