package net.corda.membership.read

import net.corda.lifecycle.Lifecycle
import net.corda.reconciliation.ReconcilerReader
import net.corda.v5.membership.GroupParameters
import net.corda.virtualnode.HoldingIdentity

interface GroupParametersReaderService : ReconcilerReader<HoldingIdentity, GroupParameters>, Lifecycle {
    fun get(identity: HoldingIdentity): GroupParameters?
}