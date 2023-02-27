package net.corda.membership.groupparams.writer.service

import net.corda.lifecycle.Lifecycle
import net.corda.messaging.api.records.Record
import net.corda.reconciliation.ReconcilerWriter
import net.corda.v5.membership.GroupParameters
import net.corda.virtualnode.HoldingIdentity

/**
 * Group parameters writer (PUBLISHER) interface.
 */
interface GroupParametersWriterService : ReconcilerWriter<HoldingIdentity, GroupParameters>, Lifecycle {
    override fun put(recordKey: HoldingIdentity, recordValue: GroupParameters)

    override fun remove(recordKey: HoldingIdentity)

    fun createRecords(recordKey: HoldingIdentity, recordValue: GroupParameters): Collection<Record<*, *>>
}