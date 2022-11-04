package net.corda.processors.db.internal.reconcile.db

import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.schema.CordaDb
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.orm.JpaEntitiesRegistry
import net.corda.orm.utils.transaction
import net.corda.processors.db.internal.reconcile.db.query.VaultReconciliationQuery
import net.corda.reconciliation.VersionedRecord
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import java.util.stream.Stream

/**
 * Database reconciler reader used for reconciling data from the Vault DB of each virtual node. This reconciler extends
 * the default base implementation for database reconciler components, [DbReconcilerReaderComponent], which holds
 * common lifecycle handling for reconciler components.
 *
 * This class accepts [vaultReconciliationQuery] as a parameter which allows custom queries/mappings to be implemented. The
 * [reconcilerQuery#invoke()] is called once per vnode and the results of each call are combine in to a single stream.
 *
 * [K] the key type of the [VersionedRecord]s created by the reconciler.
 * [V] the value type of the [VersionedRecord]s created by the reconciler.
 *
 * @param coordinatorFactory [LifecycleCoordinatorFactory] to manage lifecycle.
 * @param virtualNodeInfoReadService [VirtualNodeInfoReadService] for retrieving all virtual nodes on the cluster.
 * @param dbConnectionManager [DbConnectionManager] for connecting to the virtual node vault databases.
 * @param jpaEntitiesRegistry [JpaEntitiesRegistry] for retrieving the JpaEntitySet for the vault database
 * @param vaultReconciliationQuery A [VaultReconciliationQuery] which queries a single virtual node database and returns the versioned
 *  records. This is applied against all virtual node vault DB and the results are combined in to a single stream.
 * @param keyClass Class of the record key. Used to create the service name.
 * @param valueClass Class of the record value. Used to create the service name.
 */
@Suppress("LongParameterList")
class VirtualNodeVaultDbReconcilerReader<K : Any, V : Any>(
    coordinatorFactory: LifecycleCoordinatorFactory,
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
    private val dbConnectionManager: DbConnectionManager,
    private val jpaEntitiesRegistry: JpaEntitiesRegistry,
    private val vaultReconciliationQuery: VaultReconciliationQuery<K, V>,
    keyClass: Class<K>,
    valueClass: Class<V>,
) : DbReconcilerReaderComponent<K, V>(
    coordinatorFactory
) {

    override val name = VirtualNodeVaultDbReconcilerReader::class.java.simpleName +
            "<${keyClass.simpleName}, ${valueClass.simpleName}>"

    override val dependencies = setOf(
        LifecycleCoordinatorName.forComponent<DbConnectionManager>(),
        LifecycleCoordinatorName.forComponent<VirtualNodeInfoReadService>()
    )

    private val entitiesSet
        get() = jpaEntitiesRegistry.get(CordaDb.Vault.persistenceUnitName)
            ?: throw CordaRuntimeException(
                "persistenceUnitName ${CordaDb.Vault.persistenceUnitName} is not registered."
            )

    override fun getAllVersionedRecords(): Stream<VersionedRecord<K, V>>? {
        return try {
            virtualNodeInfoReadService
                .getAll()
                .map { vnode ->
                    val emf = dbConnectionManager.createEntityManagerFactory(
                        vnode.vaultDmlConnectionId,
                        entitiesSet
                    )
                    try {
                        emf.transaction { em ->
                            vaultReconciliationQuery.invoke(vnode, em)
                        }
                    } finally {
                        emf.close()
                    }
                }
                .flatten()
                .stream()
        } catch (e: Exception) {
            logger.warn("Error while retrieving records for reconciliation", e)
            coordinator.postEvent(GetRecordsErrorEvent(e))
            null
        }
    }

    override fun onStatusDown() = Unit
    override fun onStatusUp() = Unit
}