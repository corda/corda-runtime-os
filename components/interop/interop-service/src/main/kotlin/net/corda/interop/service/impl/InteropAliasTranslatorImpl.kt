package net.corda.interop.service.impl

import net.corda.data.p2p.HostedIdentityEntry
import net.corda.interop.service.InteropAliasTranslator
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.BlockingDominoTile
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.util.SubscriptionDominoTile
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toCorda
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

class InteropAliasTranslatorImpl(
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    subscriptionFactory: SubscriptionFactory,
    configuration: SmartConfig
) : InteropAliasTranslator {
    companion object {
        private const val GROUP_NAME = "interop_stub_hosting_map"
    }

    private val ready = CompletableFuture<Unit>()

    private val subscriptionConfig = SubscriptionConfig(
        GROUP_NAME,
        Schemas.P2P.P2P_HOSTED_IDENTITIES_TOPIC,
    )

    private val subscription = {
        subscriptionFactory.createCompactedSubscription(
            subscriptionConfig,
            Processor(),
            configuration,
        )
    }

    private val subscriptionTile = SubscriptionDominoTile(
        lifecycleCoordinatorFactory,
        subscription,
        subscriptionConfig,
        emptyList(),
        emptyList(),
    )

    private val blockingTile = BlockingDominoTile(this::class.java.simpleName, lifecycleCoordinatorFactory, ready)

    override val dominoTile = ComplexDominoTile(
        this::class.java.simpleName,
        lifecycleCoordinatorFactory,
        dependentChildren = setOf(subscriptionTile.coordinatorName, blockingTile.coordinatorName),
        managedChildren = setOf(subscriptionTile.toNamedLifecycle(), blockingTile.toNamedLifecycle())
    )

    private val identityMappingCache = ConcurrentHashMap<String, HoldingIdentity>()

    override fun getRealHoldingIdentity(recipientId: String?): HoldingIdentity? {
        return identityMappingCache[recipientId]
    }

    private inner class Processor : CompactedProcessor<String, HostedIdentityEntry> {
        override val keyClass = String::class.java
        override val valueClass = HostedIdentityEntry::class.java

        override fun onNext(
            newRecord: Record<String, HostedIdentityEntry>,
            oldValue: HostedIdentityEntry?,
            currentData: Map<String, HostedIdentityEntry>
        ) {
            if (oldValue != null) {
                identityMappingCache.remove(oldValue.holdingIdentity.x500Name.toString())
            }
            val newIdentity = newRecord.value
            if (newIdentity != null) {
                addEntry(newIdentity)
            }
        }

        override fun onSnapshot(currentData: Map<String, HostedIdentityEntry>) {
            identityMappingCache.clear()
            currentData.values.forEach {
                addEntry(it)
            }
            ready.complete(Unit)
        }
    }

    private fun addEntry(entry: HostedIdentityEntry) {
        val info = entry.toHoldingIdentity()
        identityMappingCache[entry.holdingIdentity.x500Name.toString()] = info
    }

    private fun HostedIdentityEntry.toHoldingIdentity(): HoldingIdentity {
        return this.holdingIdentity.toCorda()
    }
}