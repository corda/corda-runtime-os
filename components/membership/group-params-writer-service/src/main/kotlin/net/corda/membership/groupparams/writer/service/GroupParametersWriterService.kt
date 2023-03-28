package net.corda.membership.groupparams.writer.service

import net.corda.lifecycle.Lifecycle
import net.corda.membership.lib.InternalGroupParameters
import net.corda.reconciliation.ReconcilerWriter
import net.corda.virtualnode.HoldingIdentity

/**
 * Group parameters writer (PUBLISHER) interface.
 */
interface GroupParametersWriterService : ReconcilerWriter<HoldingIdentity, InternalGroupParameters>, Lifecycle {
    override fun put(recordKey: HoldingIdentity, recordValue: InternalGroupParameters)

    override fun remove(recordKey: HoldingIdentity)
}