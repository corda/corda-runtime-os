package net.corda.processors.db.internal.reconcile.db

import net.corda.virtualnode.HoldingIdentity
import javax.persistence.EntityManagerFactory

/**
 * Additional context to be included during reconciliation.
 */
sealed class ReconciliationContext {
    abstract val emf: EntityManagerFactory

    /**
     * Context required for reconciling cluster DBs
     */
    data class ClusterReconciliationContext(
        override val emf: EntityManagerFactory
    ) : ReconciliationContext()

    /**
     * Context required for reconciling virtual node DBs
     */
    data class VirtualNodeReconciliationContext(
        override val emf: EntityManagerFactory,
        val holdingIdentity: HoldingIdentity
    ) : ReconciliationContext()
}