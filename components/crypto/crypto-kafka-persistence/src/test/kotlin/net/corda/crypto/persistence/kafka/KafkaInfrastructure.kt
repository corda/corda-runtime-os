package net.corda.crypto.persistence.kafka

import net.corda.crypto.component.persistence.KeyValuePersistence
import net.corda.crypto.impl.config.CryptoConfigMap
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.emulation.publisher.factory.CordaPublisherFactory
import net.corda.messaging.emulation.rpc.RPCTopicService
import net.corda.messaging.emulation.rpc.RPCTopicServiceImpl
import net.corda.messaging.emulation.subscription.factory.InMemSubscriptionFactory
import net.corda.messaging.emulation.topic.service.TopicService
import net.corda.messaging.emulation.topic.service.impl.TopicServiceImpl
import net.corda.v5.cipher.suite.config.CryptoLibraryConfig
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.fail

class KafkaInfrastructure {

    companion object {
        inline fun <reified V, E> KeyValuePersistence<V, E>.wait(
            key: String,
            timeout: Duration = Duration.ofSeconds(2),
            retryDelay: Duration = Duration.ofMillis(50),
        ): V {
            val end = Instant.now().plus(timeout)
            do {
                val value = this.get(key)
                if(value != null) {
                    return value
                }
                Thread.sleep(retryDelay.toMillis())
            } while(Instant.now() < end)
            fail("Failed to wait for '$key'")
        }
    }

    private val topicService: TopicService = TopicServiceImpl()
    private val rpcTopicService: RPCTopicService = RPCTopicServiceImpl()
    private var lifecycleCoordinator: LifecycleCoordinator = mock()
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory = mock {
        on { createCoordinator(any(), any()) } doReturn lifecycleCoordinator
    }
    val subscriptionFactory: SubscriptionFactory =
        InMemSubscriptionFactory(topicService, rpcTopicService, lifecycleCoordinatorFactory)
    val publisherFactory: PublisherFactory =
        CordaPublisherFactory(topicService, rpcTopicService, lifecycleCoordinatorFactory)

    fun createSigningKeysPersistenceProvider(
        snapshot: (() -> Unit)? = null
    ): KafkaSigningKeysPersistenceProvider {
        if(snapshot != null) {
            snapshot()
        }
        return KafkaSigningKeysPersistenceProvider().also {
            it.subscriptionFactory = subscriptionFactory
            it.publisherFactory = publisherFactory
            it.start()
            it.handleConfigEvent(CryptoLibraryConfigTestImpl(emptyMap()))
        }
    }

    fun createSoftPersistenceProvider(
        snapshot: (() -> Unit)? = null
    ): KafkaSoftPersistenceProvider {
        if(snapshot != null) {
            snapshot()
        }
        return KafkaSoftPersistenceProvider().also {
            it.subscriptionFactory = subscriptionFactory
            it.publisherFactory = publisherFactory
            it.start()
            it.handleConfigEvent(CryptoLibraryConfigTestImpl(emptyMap()))
        }
    }

    inline fun <reified E : Any> getRecords(
        groupName: String,
        topic: String, expectedCount: Int = 1
    ): List<Pair<String, E>> {
        val stop = CountDownLatch(expectedCount)
        val records = mutableListOf<Pair<String, E>>()
        subscriptionFactory.createCompactedSubscription(
            subscriptionConfig = SubscriptionConfig(groupName, topic),
            processor = object : CompactedProcessor<String, E> {
                override val keyClass: Class<String> = String::class.java
                override val valueClass: Class<E> = E::class.java
                override fun onSnapshot(currentData: Map<String, E>) {
                    records.addAll(currentData.entries.map { it.key to it.value })
                    repeat(currentData.count()) {
                        stop.countDown()
                    }
                }

                override fun onNext(newRecord: Record<String, E>, oldValue: E?, currentData: Map<String, E>) {
                    records.add(newRecord.key to newRecord.value!!)
                    stop.countDown()
                }
            },
            nodeConfig = SmartConfigImpl.empty()
        ).use {
            it.start()
            stop.await(2, TimeUnit.SECONDS)
        }
        return records.toList()
    }

    inline fun <reified V, E> publish(
        clientId: String,
        persistence: KeyValuePersistence<V, E>?,
        topic: String,
        key: String,
        record: Any
    ) {
        val pub = publisherFactory.createPublisher(PublisherConfig(clientId))
        pub.publish(
            listOf(Record(topic, key, record))
        )[0].get()
        persistence?.wait(key)
        pub.close()
    }

    class CryptoLibraryConfigTestImpl(
        map: Map<String, Any?>
    ) : CryptoConfigMap(map), CryptoLibraryConfig
}