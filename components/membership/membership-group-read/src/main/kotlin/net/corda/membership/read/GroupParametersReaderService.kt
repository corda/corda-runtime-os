package net.corda.membership.read

import net.corda.lifecycle.Lifecycle
import net.corda.membership.lib.SignedGroupParameters
import net.corda.reconciliation.ReconcilerReader
import net.corda.virtualnode.HoldingIdentity

/**
 * Reconciler reader used for group parameters database reconciliation.
 * Reads records from the group parameters kafka topic.
 */
interface GroupParametersReaderService : ReconcilerReader<HoldingIdentity, SignedGroupParameters>, Lifecycle {
    fun get(identity: HoldingIdentity): SignedGroupParameters?
}