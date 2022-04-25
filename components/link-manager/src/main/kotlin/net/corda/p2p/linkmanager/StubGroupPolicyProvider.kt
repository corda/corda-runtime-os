package net.corda.p2p.linkmanager

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.util.ResourcesHolder
import net.corda.lifecycle.domino.logic.util.SubscriptionDominoTile
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.test.GroupPolicyEntry
import net.corda.schema.TestSchema.Companion.GROUP_POLICIES_TOPIC
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

internal class StubGroupPolicyProvider(
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    subscriptionFactory: SubscriptionFactory,
    configuration: SmartConfig,
) : LinkManagerGroupPolicyProvider {
    companion object {
        fun GroupPolicyEntry.toGroupInfo(): GroupPolicyListener.GroupInfo {
            return GroupPolicyListener.GroupInfo(
                this.groupId,
                this.networkType,
                this.protocolModes.toSet(),
                this.trustedCertificates
            )
        }
    }

    private val groupsSubscriptionConfig = SubscriptionConfig("group-policies-reader", GROUP_POLICIES_TOPIC)
    private val groupsSubscription = subscriptionFactory.createCompactedSubscription(
        groupsSubscriptionConfig,
        GroupProcessor(),
        configuration
    )

    private inner class GroupProcessor : CompactedProcessor<String, GroupPolicyEntry> {
        override val keyClass = String::class.java
        override val valueClass = GroupPolicyEntry::class.java

        override fun onSnapshot(currentData: Map<String, GroupPolicyEntry>) {
            groups.clear()
            currentData.values.forEach {
                addGroup(it)
            }
            readyFuture.complete(Unit)
        }

        override fun onNext(
            newRecord: Record<String, GroupPolicyEntry>,
            oldValue: GroupPolicyEntry?,
            currentData: Map<String, GroupPolicyEntry>,
        ) {
            val newValue = newRecord.value
            if (newValue == null) {
                groups.remove(oldValue?.groupId)
            } else {
                addGroup(newValue)
            }
        }

        private fun addGroup(group: GroupPolicyEntry) {
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
    private val listeners = ConcurrentHashMap.newKeySet<GroupPolicyListener>()

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

    private val groups = ConcurrentHashMap<String, GroupPolicyListener.GroupInfo>()

    override fun getGroupInfo(groupId: String): GroupPolicyListener.GroupInfo? {
        return groups[groupId]
    }

    override fun registerListener(groupPolicyListener: GroupPolicyListener) {
        listeners += groupPolicyListener
    }
}
