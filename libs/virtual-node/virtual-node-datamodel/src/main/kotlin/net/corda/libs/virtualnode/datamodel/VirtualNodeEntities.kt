package net.corda.libs.virtualnode.datamodel

import net.corda.libs.virtualnode.datamodel.entities.HoldingIdentityEntity
import net.corda.libs.virtualnode.datamodel.entities.VirtualNodeEntity
import net.corda.libs.virtualnode.datamodel.entities.VirtualNodeOperationEntity

object VirtualNodeEntities {
    val classes = setOf(
        HoldingIdentityEntity::class.java,
        VirtualNodeEntity::class.java,
        VirtualNodeOperationEntity::class.java,
    )
}
