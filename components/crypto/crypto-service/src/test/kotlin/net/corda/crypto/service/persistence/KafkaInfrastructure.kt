package net.corda.crypto.service.persistence

import net.corda.crypto.impl.config.CryptoLibraryConfigImpl
import net.corda.crypto.impl.persistence.IHaveMemberId
import net.corda.crypto.impl.persistence.KeyValuePersistence
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.messaging.emulation.publisher.factory.CordaPublisherFactory
import net.corda.messaging.emulation.subscription.factory.InMemSubscriptionFactory
import net.corda.messaging.emulation.topic.service.TopicService
import net.corda.messaging.emulation.topic.service.impl.TopicServiceImpl
import net.corda.v5.cipher.suite.config.CryptoLibraryConfig
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.fail

class KafkaInfrastructure {
    companion object {
        const val KEY_CACHE_TOPIC_NAME = "keyCacheTopic"
        const val MNG_CACHE_TOPIC_NAME = "mngCacheTopic"
        const val GROUP_NAME = "groupName"
        const val CLIENT_ID = "clientId"
        val config: CryptoLibraryConfig = CryptoLibraryConfigImpl(
            mapOf(
                "keyCache" to mapOf(
                    "persistenceConfig" to mapOf(
                        "groupName" to GROUP_NAME,
                        "topicName" to KEY_CACHE_TOPIC_NAME,
                        "clientId" to CLIENT_ID
                    )
                ),
                "mngCache" to mapOf(
                    "persistenceConfig" to mapOf(
                        "groupName" to GROUP_NAME,
                        "topicName" to MNG_CACHE_TOPIC_NAME,
                        "clientId" to CLIENT_ID
                    )

                )
            )
        )

        inline fun <reified V : IHaveMemberId, E : IHaveMemberId> KeyValuePersistence<V, E>.wait(
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
    val subscriptionFactory: SubscriptionFactory = InMemSubscriptionFactory(topicService)
    val publisherFactory: PublisherFactory = CordaPublisherFactory(topicService)

    fun createFactory(snapshot: (() -> Unit)? = null): KafkaKeyValuePersistenceFactory {
        if(snapshot != null) {
            snapshot()
        }
        return KafkaKeyValuePersistenceFactory(
            subscriptionFactory = subscriptionFactory,
            publisherFactory = publisherFactory,
            config = config
        )
    }

    inline fun <reified E : Any> getRecords(topic: String, expectedCount: Int = 1): List<Pair<String, E>> {
        val stop = CountDownLatch(expectedCount)
        val records = mutableListOf<Pair<String, E>>()
        subscriptionFactory.createCompactedSubscription(
            subscriptionConfig = SubscriptionConfig(GROUP_NAME, topic),
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

    inline fun <reified V : IHaveMemberId, E : IHaveMemberId> publish(
        persistence: KeyValuePersistence<V, E>?,
        topic: String,
        key: String,
        record: Any
    ) {
        val pub = publisherFactory.createPublisher(PublisherConfig(CLIENT_ID))
        pub.publish(
            listOf(Record(topic, key, record))
        )[0].get()
        persistence?.wait(key)
        pub.close()
    }
}