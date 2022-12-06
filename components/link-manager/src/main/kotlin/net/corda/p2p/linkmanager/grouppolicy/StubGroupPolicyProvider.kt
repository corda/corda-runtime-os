package net.corda.p2p.linkmanager.grouppolicy

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.BlockingDominoTile
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.util.SubscriptionDominoTile
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.test.GroupPolicyEntry
import net.corda.schema.Schemas.P2P.Companion.GROUP_POLICIES_TOPIC
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toCorda
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
                this.holdingIdentity.toCorda(),
                this.networkType,
                this.protocolModes.toSet(),
                this.trustedCertificates,
                GroupPolicyConstants.PolicyValues.P2PParameters.SessionPkiMode.NO_PKI,
                null,
                emptyList(),
            )
        }
    }

    private val groupsSubscriptionConfig = SubscriptionConfig("group-policies-reader", GROUP_POLICIES_TOPIC)
    private val groupsSubscription = {
        subscriptionFactory.createCompactedSubscription(
            groupsSubscriptionConfig,
            GroupProcessor(),
            configuration
        )
    }

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
                groups.remove(oldValue?.holdingIdentity?.toCorda())
            } else {
                addGroup(newValue)
            }
        }

        private fun addGroup(group: GroupPolicyEntry) {
            val info = group.toGroupInfo()
            groups[group.holdingIdentity.toCorda()] = info
            listeners.forEach {
                it.groupAdded(info)
            }
        }
    }

    private val groupSubscriptionTile = SubscriptionDominoTile(
        lifecycleCoordinatorFactory,
        groupsSubscription,
        groupsSubscriptionConfig,
        emptySet(),
        emptySet()
    )
    private val listeners = ConcurrentHashMap.newKeySet<GroupPolicyListener>()

    private val readyFuture = CompletableFuture<Unit>()
    private val blockingTile = BlockingDominoTile(
        this::class.java.simpleName,
        lifecycleCoordinatorFactory,
        readyFuture
    )

    override val dominoTile = ComplexDominoTile(
        this::class.java.simpleName,
        lifecycleCoordinatorFactory,
        dependentChildren = setOf(groupSubscriptionTile.coordinatorName, blockingTile.coordinatorName),
        managedChildren = setOf(groupSubscriptionTile.toNamedLifecycle(), blockingTile.toNamedLifecycle())
    )

    private val groups = ConcurrentHashMap<HoldingIdentity, GroupPolicyListener.GroupInfo>()

    override fun getGroupInfo(holdingIdentity: HoldingIdentity): GroupPolicyListener.GroupInfo? {
        return groups[holdingIdentity]
    }

    override fun registerListener(groupPolicyListener: GroupPolicyListener) {
        listeners += groupPolicyListener
    }
}
