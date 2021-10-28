package net.corda.p2p.linkmanager

import net.corda.lifecycle.domino.logic.DominoTile

/**
 * This interface represents a component that has knowledge about the identities that are hosted locally.
 *
 * This can be used by the [LinkManager] to identify whether a message can be simply sent
 * via a loop back mechanism to an identity hosted locally or needs to be sent through the network.
 */
interface LinkManagerHostingMap {

    fun isHostedLocally(identity: LinkManagerNetworkMap.HoldingIdentity): Boolean

    /**
     * Returns the [DominoTile] used by the CryptoService or null if a DominoTile is not used.
     */
    fun getDominoTile(): DominoTile
}