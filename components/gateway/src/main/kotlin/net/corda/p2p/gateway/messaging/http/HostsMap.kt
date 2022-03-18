package net.corda.p2p.gateway.messaging.http

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
import net.corda.p2p.test.GatewayHosts
import net.corda.schema.TestSchema.Companion.GATEWAY_HOSTS_MAP_TOPIC
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

internal class HostsMap(
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    subscriptionFactory: SubscriptionFactory,
    nodeConfiguration: SmartConfig,
    instanceId: Int,
) :
    LifecycleWithDominoTile, (String) -> Collection<String>? {

    companion object {
        private const val CONSUMER_GROUP_ID = "gateway_hosts_reader"
    }
    private val ready = CompletableFuture<Unit>()
    private val subscription = subscriptionFactory.createCompactedSubscription(
        SubscriptionConfig(CONSUMER_GROUP_ID, GATEWAY_HOSTS_MAP_TOPIC, instanceId),
        Processor(),
        nodeConfiguration
    )
    private val knownAddresses = ConcurrentHashMap<String, Collection<String>>()
    private val subscriptionTile = SubscriptionDominoTile(
        lifecycleCoordinatorFactory,
        subscription,
        emptyList(),
        emptyList()
    )

    override val dominoTile = ComplexDominoTile(
        this::class.java.simpleName,
        lifecycleCoordinatorFactory,
        createResources = ::createResources,
        managedChildren = listOf(subscriptionTile),
        dependentChildren = listOf(subscriptionTile),
    )

    override fun invoke(host: String) = knownAddresses[host]

    private fun createResources(
        @Suppress("UNUSED_PARAMETER") resources: ResourcesHolder
    ): CompletableFuture<Unit> {
        return ready
    }

    private inner class Processor : CompactedProcessor<String, GatewayHosts> {
        override val keyClass = String::class.java
        override val valueClass = GatewayHosts::class.java

        override fun onSnapshot(currentData: Map<String, GatewayHosts>) {
            knownAddresses.putAll(
                currentData.mapValues {
                    it.value.gatewayHosts
                }
            )
            ready.complete(Unit)
        }

        override fun onNext(
            newRecord: Record<String, GatewayHosts>,
            oldValue: GatewayHosts?,
            currentData: Map<String, GatewayHosts>,
        ) {
            val hosts = newRecord.value?.gatewayHosts

            if (hosts != null) {
                knownAddresses[newRecord.key] = hosts
            } else {
                knownAddresses.remove(newRecord.key)
            }
        }
    }
}
