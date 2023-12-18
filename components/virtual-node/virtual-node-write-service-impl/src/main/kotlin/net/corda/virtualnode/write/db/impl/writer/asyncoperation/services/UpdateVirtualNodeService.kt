package net.corda.virtualnode.write.db.impl.writer.asyncoperation.services

import net.corda.data.virtualnode.VirtualNodeDbConnectionUpdateRequest
import net.corda.db.connection.manager.VirtualNodeDbType
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.messaging.api.records.Record
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeDb
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeDbConnections

internal interface UpdateVirtualNodeService {

    /**
     * @return `null` if validation was successful or non-empty string explaining why validation has failed
     */
    fun validateRequest(request: VirtualNodeDbConnectionUpdateRequest): String?

    fun persistHoldingIdAndVirtualNode(
        holdingIdentity: HoldingIdentity,
        vNodeDbs: Map<VirtualNodeDbType, VirtualNodeDb>,
        cpiId: CpiIdentifier,
        updateActor: String,
        externalMessagingRouteConfig: String?
    ): VirtualNodeDbConnections

    fun publishRecords(records: List<Record<*, *>>)
}
