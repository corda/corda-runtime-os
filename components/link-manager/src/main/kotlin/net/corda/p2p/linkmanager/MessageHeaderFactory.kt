package net.corda.p2p.linkmanager

import net.corda.data.identity.HoldingIdentity
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.DominoTile
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.p2p.LinkOutHeader
import net.corda.p2p.linkmanager.LinkManagerNetworkMap.Companion.toHoldingIdentity
import net.corda.p2p.linkmanager.LinkManagerNetworkMap.Companion.toNetworkType
import net.corda.v5.base.util.contextLogger

class MessageHeaderFactory(
    private val trustStoresContainer: TrustStoresContainer,
    private val linkManagerNetworkMap: LinkManagerNetworkMap,
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
) : LifecycleWithDominoTile {
    companion object {
        private val logger = contextLogger()
    }

    fun createLinkOutHeader(
        source: HoldingIdentity,
        destination: HoldingIdentity = source,
    ): LinkOutHeader? {
        return createLinkOutHeader(
            source.toHoldingIdentity(),
            destination.toHoldingIdentity(),
        )
    }

    fun createLinkOutHeader(
        source: LinkManagerNetworkMap.HoldingIdentity,
        destination: LinkManagerNetworkMap.HoldingIdentity = source,
    ): LinkOutHeader? {
        val destMemberInfo = linkManagerNetworkMap.getMemberInfo(destination)
        if (destMemberInfo == null) {
            logger.warn("Attempted to send message to peer $destination which is not in the network map. The message was discarded.")
            return null
        }
        val trustStoreHash = trustStoresContainer[source]
        if (trustStoreHash == null) {
            logger.warn("Attempted to send message to peer $destination which has no trust store. The message was discarded.")
            return null
        }

        val networkType = linkManagerNetworkMap.getNetworkType(source.groupId)
        if (networkType == null) {
            logger.warn("Could not find the network type in the NetworkMap for $source. The message was discarded.")
            return null
        }

        return LinkOutHeader(
            destMemberInfo.holdingIdentity.x500Name,
            networkType.toNetworkType(),
            destMemberInfo.endPoint.address,
            trustStoreHash
        )
    }

    override val dominoTile = DominoTile(
        this.javaClass.simpleName,
        lifecycleCoordinatorFactory,
        children = listOf(
            linkManagerNetworkMap.dominoTile,
            trustStoresContainer.dominoTile,
        )

    )
}
