package net.corda.processors.db.internal.reconcile.db

import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.virtualnode.datamodel.VirtualNodeEntity
import net.corda.libs.virtualnode.datamodel.findAllVirtualNodes
import net.corda.reconciliation.VersionedRecord
import net.corda.v5.base.types.MemberX500Name
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
                    vaultDmlConnectionId = entity.vaultDMLConnectionId!!,
                    cryptoDmlConnectionId = entity.cryptoDMLConnectionId!!,
                    uniquenessDmlConnectionId = entity.uniquenessDMLConnectionId!!,
                    vaultDdlConnectionId = entity.vaultDDLConnectionId,
                    cryptoDdlConnectionId = entity.cryptoDDLConnectionId,
                    uniquenessDdlConnectionId = entity.uniquenessDDLConnectionId,
                    version = entity.entityVersion,
                    timestamp = entity.insertTimestamp,
                    flowP2pOperationalStatus = entity.flowP2pOperationalStatus.name,
                    flowStartOperationalStatus = entity.flowStartOperationalStatus.name,
                    flowOperationalStatus = entity.flowOperationalStatus.name,
                    vaultDbOperationalStatus = entity.vaultDbOperationalStatus.name,
                    operationInProgress = entity.operationInProgress?.id//todo conal - should this be requestId?
                )
            }
        }
    }


private fun Instant?.getOrNow(): Instant {
    return this ?: Instant.now()
}
