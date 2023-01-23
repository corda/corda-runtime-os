package net.corda.libs.virtualnode.datamodel.repository

import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.ShortHash
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.VirtualNodeState
import java.util.stream.Stream
import javax.persistence.EntityManager

// using an interface allows us to easily mock/test
interface VirtualNodeRepository {
    fun findAll(entityManager: EntityManager): Stream<VirtualNodeInfo>
    fun find(entityManager: EntityManager, holdingIdentityShortHash: ShortHash): VirtualNodeInfo?
    fun put(entityManager: EntityManager, holdingId: HoldingIdentity, cpiId: CpiIdentifier)
    fun updateVirtualNodeState(entityManager: EntityManager, holdingIdentityShortHash: String, newState: VirtualNodeState): VirtualNodeInfo
    fun otherGroupsExists(entityManager: EntityManager, groupId: String): Boolean
}

