package net.corda.processors.db.internal.reconcile.db.query

import net.corda.reconciliation.VersionedRecord
import net.corda.virtualnode.VirtualNodeInfo
import javax.persistence.EntityManager

/**
 * Interface for queries to be done using an [EntityManager] for a virtual node's vault database.
 */
interface VaultReconciliationQuery<K : Any, V : Any> {
    /**
     * Function which performs a query, using a given entity manager, for a single virtual node. The vault database
     * reconciliation will iterate over the available virtual nodes and apply this query to each virtual node's
     * database.
     *
     * This function must return results of the query as a collection of [VersionedRecord]s. The vault database
     * reconciler class is responsible for combining the results from all vault databases into a single stream.
     *
     * @param vnodeInfo The [VirtualNodeInfo] object which gives context about which virtual node the given entity
     *  manager is relevant for.
     * @param em The [EntityManager] use to query the vault database for the given virtual node.
     */
    fun invoke(
        vnodeInfo: VirtualNodeInfo,
        em: EntityManager
    ): Collection<VersionedRecord<K, V>>
}