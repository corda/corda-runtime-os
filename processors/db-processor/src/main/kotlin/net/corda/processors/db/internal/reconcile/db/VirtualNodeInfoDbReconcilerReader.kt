package net.corda.processors.db.internal.reconcile.db

import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.virtualnode.datamodel.VirtualNodeEntity
import net.corda.libs.virtualnode.datamodel.findAllVirtualNodes
import net.corda.reconciliation.VersionedRecord
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import java.time.Instant
import java.util.stream.Stream
import javax.persistence.EntityManager

/**
 * Gets and converts the database entity classes to 'Corda' classes
 */
val getAllVirtualNodesDBVersionedRecords: (EntityManager) -> Stream<VersionedRecord<HoldingIdentity, VirtualNodeInfo>> =
    { em -> virtualNodeEntitiesToVersionedRecords(em.findAllVirtualNodes()) }

fun virtualNodeEntitiesToVersionedRecords(virtualNodes: Stream<VirtualNodeEntity>)
        : Stream<VersionedRecord<HoldingIdentity, VirtualNodeInfo>> =
    virtualNodes.map { entity ->
        val x500Name = entity.holdingIdentity.x500Name
        val groupId = entity.holdingIdentity.mgmGroupId
        val holdingIdentity = HoldingIdentity(x500Name, groupId)

        object : VersionedRecord<HoldingIdentity, VirtualNodeInfo> {
            override val version = entity.entityVersion
            override val isDeleted = entity.isDeleted
            override val key = holdingIdentity
            override val value by lazy {
                val signerSummaryHash = if (entity.cpiSignerSummaryHash.isNotBlank()) {
                    SecureHash.create(entity.cpiSignerSummaryHash)
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
                    vaultDdlConnectionId = entity.holdingIdentity.vaultDDLConnectionId,
                    cryptoDdlConnectionId = entity.holdingIdentity.cryptoDDLConnectionId,
                    version = entity.entityVersion,
                    timestamp = entity.insertTimestamp.getOrNow()
                )
            }
        }
    }


private fun Instant?.getOrNow(): Instant {
    return this ?: Instant.now()
}
