package net.corda.processors.db.internal.reconcile.db

import net.corda.libs.virtualnode.datamodel.VirtualNodeRepositoryImpl
import net.corda.reconciliation.VersionedRecord
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import java.util.stream.Stream

/**
 * Gets and converts the database entity classes to 'Corda' classes
 */
val getAllVirtualNodesDBVersionedRecords
        : (ReconciliationContext) -> Stream<VersionedRecord<HoldingIdentity, VirtualNodeInfo>> =
    { context ->
        virtualNodeEntitiesToVersionedRecords(VirtualNodeRepositoryImpl().findAll(context.entityManager))
    }

fun virtualNodeEntitiesToVersionedRecords(virtualNodes: Stream<VirtualNodeInfo>)
        : Stream<VersionedRecord<HoldingIdentity, VirtualNodeInfo>> =
    virtualNodes.map { entity ->
        object : VersionedRecord<HoldingIdentity, VirtualNodeInfo> {
            override val version = entity.version
            override val isDeleted = entity.isDeleted
            override val key = entity.holdingIdentity
            override val value = entity
        }
    }
