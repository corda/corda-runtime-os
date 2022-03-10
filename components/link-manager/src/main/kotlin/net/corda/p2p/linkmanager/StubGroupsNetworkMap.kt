package net.corda.p2p.linkmanager

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.lifecycle.domino.logic.util.ResourcesHolder
import net.corda.lifecycle.domino.logic.util.SubscriptionDominoTile
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.test.GroupNetworkMapEntry
import net.corda.schema.TestSchema.Companion.GROUP_NETWORK_MAP_TOPIC
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

internal class StubGroupsNetworkMap(
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    subscriptionFactory: SubscriptionFactory,
    instanceId: Int,
    configuration: SmartConfig,
) : LifecycleWithDominoTile {

    private val groupsSubscriptionConfig = SubscriptionConfig("network-map", GROUP_NETWORK_MAP_TOPIC, instanceId)
    private val groupsSubscription = subscriptionFactory.createCompactedSubscription(
        groupsSubscriptionConfig,
        GroupProcessor(),
        configuration
    )

    private inner class GroupProcessor : CompactedProcessor<String, GroupNetworkMapEntry> {
        override val keyClass = String::class.java
        override val valueClass = GroupNetworkMapEntry::class.java

        override fun onSnapshot(currentData: Map<String, GroupNetworkMapEntry>) {
            groups.clear()
            currentData.values.forEach {
                addGroup(it)
            }
            readyFuture.complete(Unit)
        }

        override fun onNext(
            newRecord: Record<String, GroupNetworkMapEntry>,
            oldValue: GroupNetworkMapEntry?,
            currentData: Map<String, GroupNetworkMapEntry>,
        ) {
            val newValue = newRecord.value
            if (newValue == null) {
                groups.remove(oldValue?.groupId)
            } else {
                addGroup(newValue)
            }
        }

        private fun addGroup(group: GroupNetworkMapEntry) {
            val info = group.toGroupInfo()
            groups[group.groupId] = info
            listeners.forEach {
                it.groupAdded(info)
            }
        }
    }

    private val groupSubscriptionTile = SubscriptionDominoTile(
        lifecycleCoordinatorFactory,
        groupsSubscription,
        emptySet(),
        emptySet()
    )
    private val listeners = ConcurrentHashMap.newKeySet<NetworkMapListener>()

    private val readyFuture = CompletableFuture<Unit>()
    override val dominoTile = ComplexDominoTile(
        this::class.java.simpleName,
        lifecycleCoordinatorFactory,
        ::createResources,
        setOf(groupSubscriptionTile),
        setOf(groupSubscriptionTile)
    )

    private fun createResources(@Suppress("UNUSED_PARAMETER") resources: ResourcesHolder): CompletableFuture<Unit> {
        return readyFuture
    }

    private val groups = ConcurrentHashMap<String, NetworkMapListener.GroupInfo>()

    fun getGroupInfo(groupId: String): NetworkMapListener.GroupInfo? {
        return groups[groupId]
    }

    fun registerListener(networkMapListener: NetworkMapListener) {
        listeners += networkMapListener
    }

    private fun GroupNetworkMapEntry.toGroupInfo(): NetworkMapListener.GroupInfo {
        return NetworkMapListener.GroupInfo(
            this.groupId,
            this.networkType,
            this.protocolModes.toSet(),
            this.trustedCertificates
        )
    }
}
