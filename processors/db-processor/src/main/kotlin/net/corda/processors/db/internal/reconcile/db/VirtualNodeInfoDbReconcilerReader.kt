package net.corda.processors.db.internal.reconcile.db

import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.virtualnode.datamodel.VirtualNodeEntity
import net.corda.libs.virtualnode.datamodel.findAllVirtualNodes
import net.corda.reconciliation.VersionedRecord
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.VirtualNodeState
import java.time.Instant
import java.util.stream.Stream

/**
 * Gets and converts the database entity classes to 'Corda' classes
 */
val getAllVirtualNodesDBVersionedRecords
        : (ReconciliationContext) -> Stream<VersionedRecord<HoldingIdentity, VirtualNodeInfo>> =
    { context -> virtualNodeEntitiesToVersionedRecords(context.entityManager.findAllVirtualNodes()) }

fun virtualNodeEntitiesToVersionedRecords(virtualNodes: Stream<VirtualNodeEntity>)
        : Stream<VersionedRecord<HoldingIdentity, VirtualNodeInfo>> =
    virtualNodes.map { entity ->
        val x500Name = entity.holdingIdentity.x500Name
        val groupId = entity.holdingIdentity.mgmGroupId
        val holdingIdentity = HoldingIdentity(MemberX500Name.parse(x500Name), groupId)

        object : VersionedRecord<HoldingIdentity, VirtualNodeInfo> {
            override val version = entity.entityVersion
            override val isDeleted = entity.isDeleted
            override val key = holdingIdentity
            override val value by lazy {
                val signerSummaryHash = if (entity.cpiSignerSummaryHash.isNotBlank()) {
                    SecureHash.parse(entity.cpiSignerSummaryHash)
                } else {
                    null
                }
                VirtualNodeInfo(
                    holdingIdentity = holdingIdentity,
                    cpiIdentifier = CpiIdentifier(
                        entity.cpiName,
                        entity.cpiVersion,
                        signerSummaryHash
                    ),
                    vaultDmlConnectionId = entity.holdingIdentity.vaultDMLConnectionId!!,
                    cryptoDmlConnectionId = entity.holdingIdentity.cryptoDMLConnectionId!!,
                    uniquenessDmlConnectionId = entity.holdingIdentity.uniquenessDMLConnectionId!!,
                    vaultDdlConnectionId = entity.holdingIdentity.vaultDDLConnectionId,
                    cryptoDdlConnectionId = entity.holdingIdentity.cryptoDDLConnectionId,
                    uniquenessDdlConnectionId = entity.holdingIdentity.uniquenessDDLConnectionId,
                    version = entity.entityVersion,
                    timestamp = entity.insertTimestamp.getOrNow(),
                    state = VirtualNodeState.valueOf(entity.virtualNodeState),
                )
            }
        }
    }


private fun Instant?.getOrNow(): Instant {
    return this ?: Instant.now()
}
