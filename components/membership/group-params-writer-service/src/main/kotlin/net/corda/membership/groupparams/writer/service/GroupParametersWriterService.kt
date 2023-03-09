package net.corda.membership.groupparams.writer.service

import net.corda.lifecycle.Lifecycle
import net.corda.membership.lib.SignedGroupParameters
import net.corda.reconciliation.ReconcilerWriter
import net.corda.virtualnode.HoldingIdentity

/**
 * Group parameters writer (PUBLISHER) interface.
 */
interface GroupParametersWriterService : ReconcilerWriter<HoldingIdentity, SignedGroupParameters>, Lifecycle {
    override fun put(recordKey: HoldingIdentity, recordValue: SignedGroupParameters)

    override fun remove(recordKey: HoldingIdentity)
}