package net.corda.p2p.linkmanager

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.NetworkType
import net.corda.p2p.crypto.ProtocolMode

internal class StubNetworkMap(
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    subscriptionFactory: SubscriptionFactory,
    instanceId: Int,
    configuration: SmartConfig,
) : LinkManagerNetworkMap {

    private val identities = StubIdentitiesNetworkMap(
        lifecycleCoordinatorFactory,
        subscriptionFactory,
        instanceId,
        configuration
    )

    private val groups = StubGroupsNetworkMap(
        lifecycleCoordinatorFactory,
        subscriptionFactory,
        instanceId,
        configuration
    )

    override val dominoTile = ComplexDominoTile(
        this::class.java.simpleName,
        lifecycleCoordinatorFactory,
        managedChildren = setOf(groups.dominoTile, identities.dominoTile),
        dependentChildren = setOf(groups.dominoTile, identities.dominoTile),
    )

    override fun getMemberInfo(holdingIdentity: LinkManagerNetworkMap.HoldingIdentity): LinkManagerNetworkMap.MemberInfo? {
        if (!isRunning) {
            throw IllegalStateException("getMemberInfo operation invoked while component was stopped.")
        }

        return identities.getMemberInfo(holdingIdentity)
    }

    override fun getMemberInfo(hash: ByteArray, groupId: String): LinkManagerNetworkMap.MemberInfo? {
        if (!isRunning) {
            throw IllegalStateException("getMemberInfo operation invoked while component was stopped.")
        }
        return identities.getMemberInfo(hash, groupId)
    }

    override fun getNetworkType(groupId: String): LinkManagerNetworkMap.NetworkType? {
        if (!isRunning) {
            throw IllegalStateException("getNetworkType operation invoked while component was stopped.")
        }

        return groups.getGroupInfo(groupId)?.networkType?.toLMNetworkType()
    }

    override fun getProtocolModes(groupId: String): Set<ProtocolMode>? {
        if (!isRunning) {
            throw IllegalStateException("getNetworkType operation invoked while component was stopped.")
        }

        return groups.getGroupInfo(groupId)?.protocolModes
    }

    override fun registerListener(networkMapListener: NetworkMapListener) {
        groups.registerListener(networkMapListener)
    }

    private fun NetworkType.toLMNetworkType(): LinkManagerNetworkMap.NetworkType {
        return when (this) {
            NetworkType.CORDA_4 -> LinkManagerNetworkMap.NetworkType.CORDA_4
            NetworkType.CORDA_5 -> LinkManagerNetworkMap.NetworkType.CORDA_5
        }
    }
}
