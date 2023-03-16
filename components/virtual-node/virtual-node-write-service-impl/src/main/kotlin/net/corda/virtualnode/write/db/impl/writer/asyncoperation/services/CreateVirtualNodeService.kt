package net.corda.virtualnode.write.db.impl.writer.asyncoperation.services

import net.corda.data.virtualnode.VirtualNodeCreateRequest
import net.corda.db.connection.manager.VirtualNodeDbType
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.messaging.api.records.Record
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.write.db.impl.writer.CpiMetadataLite
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeDb
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeDbConnections

internal interface CreateVirtualNodeService {

    fun validateRequest(request: VirtualNodeCreateRequest): String?

    fun ensureHoldingIdentityIsUnique(request: VirtualNodeCreateRequest)

    fun getCpiMetaData(cpiFileChecksum: String): CpiMetadataLite

    fun runCpiMigrations(cpiMetadata: CpiMetadataLite, vaultDb: VirtualNodeDb, holdingIdentity: HoldingIdentity)

    fun persistHoldingIdAndVirtualNode(
        holdingIdentity: HoldingIdentity,
        vNodeDbs: Map<VirtualNodeDbType,VirtualNodeDb>,
        cpiId: CpiIdentifier,
        updateActor: String
    ): VirtualNodeDbConnections

    fun publishRecords(records: List<Record<*, *>>)
}