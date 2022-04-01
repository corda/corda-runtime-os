package net.corda.p2p.linkmanager

import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile

/**
 * This interface represents a component that has knowledge about the identities that are hosted locally.
 *
 * This can be used by the [LinkManager] to identify whether a message can be simply sent
 * via a loop back mechanism to an identity hosted locally or needs to be sent through the network.
 */
interface LinkManagerHostingMap : LifecycleWithDominoTile {

    fun isHostedLocally(identity: LinkManagerInternalTypes.HoldingIdentity): Boolean

    fun getInfo(identity: LinkManagerInternalTypes.HoldingIdentity): HostingMapListener.IdentityInfo?

    fun getInfo(hash: ByteArray, groupId: String): HostingMapListener.IdentityInfo?

    fun registerListener(listener: HostingMapListener)
}
