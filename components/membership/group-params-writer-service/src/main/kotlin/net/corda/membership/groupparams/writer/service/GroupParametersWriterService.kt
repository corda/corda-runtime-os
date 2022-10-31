package net.corda.membership.groupparams.writer.service

import net.corda.data.membership.GroupParametersOwner
import net.corda.lifecycle.Lifecycle
import net.corda.reconciliation.ReconcilerWriter
import net.corda.v5.membership.GroupParameters

/**
 * Group parameters writer (PUBLISHER) interface.
 */
interface GroupParametersWriterService : ReconcilerWriter<GroupParametersOwner, GroupParameters>, Lifecycle {
    override fun put(recordKey: GroupParametersOwner, recordValue: GroupParameters)

    override fun remove(recordKey: GroupParametersOwner)
}