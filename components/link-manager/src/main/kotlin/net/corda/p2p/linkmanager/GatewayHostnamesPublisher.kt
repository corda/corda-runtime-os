package net.corda.p2p.linkmanager

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.lifecycle.domino.logic.util.PublisherWithDominoLogic
import net.corda.lifecycle.domino.logic.util.ResourcesHolder
import net.corda.lifecycle.domino.logic.util.SubscriptionDominoTile
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.test.GatewayHosts
import net.corda.schema.TestSchema.Companion.GATEWAY_HOSTS_MAP_TOPIC
import java.net.URL
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

@Suppress("LongParameterList")
internal class GatewayHostnamesPublisher(
    subscriptionFactory: SubscriptionFactory,
    publisherFactory: PublisherFactory,
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    configuration: SmartConfig,
    instanceId: Int,
) : LifecycleWithDominoTile, MembershipGroupListener {

    companion object {
        private const val CURRENT_DATA_READER_GROUP_NAME = "linkmanager_hostnames_reader"
        private const val MISSING_DATA_WRITER_GROUP_NAME = "linkmanager_hostnames_writer"
    }

    private val publishedNames = ConcurrentHashMap<String, Set<String>>()
    private val toPublish = ConcurrentLinkedQueue<LinkManagerInternalTypes.MemberInfo>()

    private val publisher = PublisherWithDominoLogic(
        publisherFactory,
        lifecycleCoordinatorFactory,
        PublisherConfig(MISSING_DATA_WRITER_GROUP_NAME),
        configuration,
    )

    private fun publishIfNeeded(member: LinkManagerInternalTypes.MemberInfo) {
        val host = URL(member.endPoint.address).host
        publishedNames.compute(host) { address, publishedAddresses ->
            val addressList = member.gatewayHosts
            val addressSets = addressList?.toSet()
            if (addressSets != publishedAddresses) {
                val hosts = if (addressList == null) null else GatewayHosts(addressList.toList())
                val record = Record(
                    GATEWAY_HOSTS_MAP_TOPIC, address,
                    hosts,
                )
                publisher.publish(
                    listOf(record)
                ).forEach {
                    it.join()
                }
            }
            addressSets
        }
    }

    private val ready = CompletableFuture<Unit>()

    override fun memberAdded(memberInfo: LinkManagerInternalTypes.MemberInfo) {
        toPublish.offer(memberInfo)
        publishQueueIfPossible()
    }

    private inner class Processor : CompactedProcessor<String, GatewayHosts> {
        override val keyClass = String::class.java
        override val valueClass = GatewayHosts::class.java
        override fun onSnapshot(currentData: Map<String, GatewayHosts>) {
            publishedNames.putAll(
                currentData.mapValues {
                    it.value.gatewayHosts.toSet()
                }
            )
            ready.complete(Unit)
            publishQueueIfPossible()
        }

        override fun onNext(
            newRecord: Record<String, GatewayHosts>,
            oldValue: GatewayHosts?,
            currentData: Map<String, GatewayHosts>,
        ) {
            val addresses = newRecord.value?.gatewayHosts
            if (addresses == null) {
                publishedNames.remove(newRecord.key)
            } else {
                publishedNames[newRecord.key] = addresses.toSet()
            }
        }
    }
    private val subscription = subscriptionFactory.createCompactedSubscription(
        SubscriptionConfig(CURRENT_DATA_READER_GROUP_NAME, GATEWAY_HOSTS_MAP_TOPIC, instanceId),
        Processor(),
        configuration,
    )
    private val subscriptionDominoTile = SubscriptionDominoTile(
        lifecycleCoordinatorFactory,
        subscription,
        emptyList(),
        emptyList(),
    )

    override val dominoTile = ComplexDominoTile(
        this.javaClass.simpleName,
        lifecycleCoordinatorFactory,
        createResources = ::createResources,
        managedChildren = listOf(
            publisher.dominoTile,
            subscriptionDominoTile,
        ),
        dependentChildren = listOf(
            publisher.dominoTile,
            subscriptionDominoTile,
        )
    )

    private fun createResources(
        @Suppress("UNUSED_PARAMETER")
        resourcesHolder: ResourcesHolder
    ): CompletableFuture<Unit> {
        publishQueueIfPossible()
        return ready
    }

    private fun publishQueueIfPossible() {
        while ((publisher.isRunning) && (ready.isDone)) {
            val identityInfo = toPublish.poll() ?: return
            publishIfNeeded(identityInfo)
        }
    }
}
