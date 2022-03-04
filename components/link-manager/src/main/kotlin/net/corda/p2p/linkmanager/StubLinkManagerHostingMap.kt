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
import net.corda.p2p.linkmanager.LinkManagerNetworkMap.Companion.toHoldingIdentity
import net.corda.p2p.test.HostingIdentityEntry
import net.corda.schema.TestSchema.Companion.HOSTING_MAP_TOPIC
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

class StubLinkManagerHostingMap(
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    subscriptionFactory: SubscriptionFactory,
    instanceId: Int,
    configuration: SmartConfig,
) : LinkManagerHostingMap {
    companion object {
        private const val GROUP_NAME = "linkmanager_stub_hosting_map"
    }

    private val locallyHostedIdentityToTenantId = ConcurrentHashMap<LinkManagerNetworkMap.HoldingIdentity, String>()
    private val listeners = ConcurrentHashMap.newKeySet<HostingMapListener>()
    private val ready = CompletableFuture<Unit>()
    private val subscription = subscriptionFactory.createCompactedSubscription(
        SubscriptionConfig(
            GROUP_NAME,
            HOSTING_MAP_TOPIC,
            instanceId
        ),
        Processor(),
        configuration,
    )

    private val subscriptionTile = SubscriptionDominoTile(
        lifecycleCoordinatorFactory,
        subscription,
        emptyList(),
        emptyList(),
    )

    override val dominoTile = ComplexDominoTile(
        this::class.java.simpleName,
        lifecycleCoordinatorFactory,
        ::createResources,
        setOf(subscriptionTile),
        setOf(subscriptionTile)
    )

    private fun createResources(
        @Suppress("UNUSED_PARAMETER")
        resourcesHolder: ResourcesHolder
    ): CompletableFuture<Unit> {
        return ready
    }

    override fun isHostedLocally(identity: LinkManagerNetworkMap.HoldingIdentity) =
        locallyHostedIdentityToTenantId.containsKey(identity)

    override fun getTenantId(identity: LinkManagerNetworkMap.HoldingIdentity) =
        locallyHostedIdentityToTenantId[identity]

    override fun registerListener(listener: HostingMapListener) {
        listeners.add(listener)
    }

    private inner class Processor : CompactedProcessor<String, HostingIdentityEntry> {
        override val keyClass = String::class.java
        override val valueClass = HostingIdentityEntry::class.java
        override fun onSnapshot(currentData: Map<String, HostingIdentityEntry>) {
            locallyHostedIdentityToTenantId.clear()
            currentData.values.forEach {
                addEntry(it)
            }
            ready.complete(Unit)
        }

        override fun onNext(
            newRecord: Record<String, HostingIdentityEntry>,
            oldValue: HostingIdentityEntry?,
            currentData: Map<String, HostingIdentityEntry>,
        ) {
            if (oldValue != null) {
                locallyHostedIdentityToTenantId.remove(oldValue.holdingIdentity.toHoldingIdentity())
            }
            val newIdentity = newRecord.value
            if (newIdentity != null) {
                addEntry(newIdentity)
            }
        }
    }

    private fun addEntry(entry: HostingIdentityEntry) {
        locallyHostedIdentityToTenantId[entry.holdingIdentity.toHoldingIdentity()] = entry.identityTenantId
        val info = HostingMapListener.IdentityInfo(
            holdingIdentity = entry.holdingIdentity,
            tlsCertificates = entry.tlsCertificates,
            tlsTenantId = entry.tlsTenantId,
            identityTenantId = entry.identityTenantId
        )
        listeners.forEach {
            it.identityAdded(info)
        }
    }
}
