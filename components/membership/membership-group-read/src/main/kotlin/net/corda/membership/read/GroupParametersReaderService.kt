package net.corda.membership.read

import net.corda.lifecycle.Lifecycle
import net.corda.membership.lib.InternalGroupParameters
import net.corda.membership.lib.SignedGroupParameters
import net.corda.reconciliation.ReconcilerReader
import net.corda.virtualnode.HoldingIdentity

/**
 * Reconciler reader used for group parameters database reconciliation.
 * Reads records from the group parameters kafka topic.
 */
interface GroupParametersReaderService : ReconcilerReader<HoldingIdentity, InternalGroupParameters>, Lifecycle {
    fun get(identity: HoldingIdentity): InternalGroupParameters?

    fun getSigned(identity: HoldingIdentity): SignedGroupParameters?
}