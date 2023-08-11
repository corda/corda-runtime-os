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

    /**
     * Checks if the given [identity] is hosted locally. Returns true if [identity] is found in the hosting
     * map. Intended to be used in scenarios where [isHostedLocallyAndSessionKeyMatch] cannot be used due to
     * unavailability of the identity's [MemberInfo].
     */
    fun isHostedLocally(identity: HoldingIdentity): Boolean

    /**
     * Checks if the given [member] is hosted locally. Performs a stricter check than [isHostedLocally] by comparing
     * [member]'s session keys with session keys of the matching identity from the hosting map. This is the preferred
     * check if the identity's [MemberInfo] is available.
     */
    fun isHostedLocallyAndSessionKeyMatch(member: MemberInfo): Boolean

    fun getInfo(identity: HoldingIdentity): HostingMapListener.IdentityInfo?

    fun getInfo(hash: ByteArray, groupId: String): HostingMapListener.IdentityInfo?

    fun registerListener(listener: HostingMapListener)

    fun allLocallyHostedIdentities(): Collection<HoldingIdentity>
}
