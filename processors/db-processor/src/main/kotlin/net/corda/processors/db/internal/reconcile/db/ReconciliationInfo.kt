package net.corda.processors.db.internal.reconcile.db

import net.corda.virtualnode.HoldingIdentity
import javax.persistence.EntityManagerFactory

sealed class ReconciliationInfo {
    abstract val emf: EntityManagerFactory

    data class ClusterReconciliationInfo(
        override val emf: EntityManagerFactory
    ) : ReconciliationInfo()

    data class VirtualNodeReconciliationInfo(
        override val emf: EntityManagerFactory,
        val holdingIdentity: HoldingIdentity
    ) : ReconciliationInfo()
}