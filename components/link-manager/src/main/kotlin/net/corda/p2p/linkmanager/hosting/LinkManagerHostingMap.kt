package net.corda.p2p.linkmanager.hosting

import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity

/**
 * This interface represents a component that has knowledge about the identities that are hosted locally.
 *
 * This can be used by the [LinkManager] to identify whether a message can be simply sent
 * via a loop back mechanism to an identity hosted locally or needs to be sent through the network.
 */
interface LinkManagerHostingMap : LifecycleWithDominoTile {

    fun isHostedLocally(identity: HoldingIdentity): Boolean

    fun isHostedLocally(member: MemberInfo?): Boolean

    fun getInfo(identity: HoldingIdentity): HostingMapListener.IdentityInfo?

    fun getInfo(hash: ByteArray, groupId: String): HostingMapListener.IdentityInfo?

    fun registerListener(listener: HostingMapListener)

    fun allLocallyHostedIdentities(): Collection<HoldingIdentity>
}
