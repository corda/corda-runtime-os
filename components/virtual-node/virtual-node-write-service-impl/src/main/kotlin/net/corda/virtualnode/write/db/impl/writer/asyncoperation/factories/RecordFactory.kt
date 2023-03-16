package net.corda.virtualnode.write.db.impl.writer.asyncoperation.factories

import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.messaging.api.records.Record
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeDbConnections

internal interface RecordFactory {
    fun createVirtualNodeInfoRecord(
        holdingIdentity: HoldingIdentity,
        cpiIdentifier: CpiIdentifier,
        dbConnections: VirtualNodeDbConnections
    ): Record<*, *>

    fun createMgmInfoRecord(
        holdingIdentity: HoldingIdentity,
        mgmInfo: MemberInfo
    ): Record<*, *>
}

