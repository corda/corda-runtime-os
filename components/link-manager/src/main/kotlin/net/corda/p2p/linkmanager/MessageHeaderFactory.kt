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
    enum class ErrorType {
        NoInfo,
        NoTrustStore,
        NoType,
    }
    interface ReportIssue {
        fun report(type: ErrorType, identity: LinkManagerNetworkMap.HoldingIdentity)
    }

    private class DefaultReporter : ReportIssue {
        override fun report(type: ErrorType, identity: LinkManagerNetworkMap.HoldingIdentity) {
            when (type) {
                ErrorType.NoInfo -> {
                    logger.warn(
                        "Attempted to send message to peer ${identity.toHoldingIdentity()} " +
                            "which is not in the network map. The message was discarded."
                    )
                }
                ErrorType.NoTrustStore -> {
                    logger.warn(
                        "Attempted to send message to peer ${identity.groupId} " +
                            "which has no trust store. The message was discarded."
                    )
                }
                ErrorType.NoType -> {
                    logger.warn(
                        "Could not find the network type in the NetworkMap for" +
                            " ${identity.toHoldingIdentity()}. The message was discarded."
                    )
                }
            }
        }
    }
    companion object {
        private val logger = contextLogger()
    }

    fun createLinkOutHeader(
        source: HoldingIdentity,
        destination: HoldingIdentity = source,
        reporter: ReportIssue? = DefaultReporter(),
    ): LinkOutHeader? {
        return createLinkOutHeader(
            source.toHoldingIdentity(),
            destination.toHoldingIdentity(),
            reporter,
        )
    }

    fun createLinkOutHeader(
        source: LinkManagerNetworkMap.HoldingIdentity,
        destination: LinkManagerNetworkMap.HoldingIdentity = source,
        reporter: ReportIssue? = DefaultReporter()
    ): LinkOutHeader? {
        val destMemberInfo = linkManagerNetworkMap.getMemberInfo(destination)
        if (destMemberInfo == null) {
            reporter?.report(ErrorType.NoInfo, destination)
            return null
        }
        val trustStoreHash = trustStoresContainer.computeTrustStoreHash(source)
        if (trustStoreHash == null) {
            reporter?.report(ErrorType.NoTrustStore, source)
            return null
        }

        val networkType = linkManagerNetworkMap.getNetworkType(source.groupId)
        if (networkType == null) {
            reporter?.report(ErrorType.NoType, source)
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
