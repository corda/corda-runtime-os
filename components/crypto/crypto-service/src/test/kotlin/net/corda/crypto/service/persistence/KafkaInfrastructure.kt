package net.corda.crypto.service.persistence

import net.corda.crypto.impl.config.CryptoLibraryConfigImpl
import net.corda.crypto.impl.config.DefaultConfigConsts
import net.corda.crypto.impl.config.defaultCryptoService
import net.corda.crypto.impl.config.publicKeys
import net.corda.crypto.impl.persistence.IHaveTenantId
import net.corda.crypto.impl.persistence.KeyValuePersistence
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
        fun cryptoSvcTopicName(config: CryptoLibraryConfig) =
            config.defaultCryptoService.persistenceConfig.getString(
                DefaultConfigConsts.Kafka.TOPIC_NAME_KEY,
                DefaultConfigConsts.Kafka.DefaultCryptoService.TOPIC_NAME
            )
        fun cryptoSvcGroupName(config: CryptoLibraryConfig) =
            config.defaultCryptoService.persistenceConfig.getString(
                DefaultConfigConsts.Kafka.GROUP_NAME_KEY,
                DefaultConfigConsts.Kafka.DefaultCryptoService.GROUP_NAME
            )
        fun cryptoSvcClientId(config: CryptoLibraryConfig) =
            config.defaultCryptoService.persistenceConfig.getString(
                DefaultConfigConsts.Kafka.CLIENT_ID_KEY,
                DefaultConfigConsts.Kafka.DefaultCryptoService.CLIENT_ID
            )
        fun signingTopicName(config: CryptoLibraryConfig) =
            config.publicKeys.persistenceConfig.getString(
                DefaultConfigConsts.Kafka.TOPIC_NAME_KEY,
                DefaultConfigConsts.Kafka.Signing.TOPIC_NAME
            )
        fun signingGroupName(config: CryptoLibraryConfig) =
            config.publicKeys.persistenceConfig.getString(
                DefaultConfigConsts.Kafka.GROUP_NAME_KEY,
                DefaultConfigConsts.Kafka.DefaultCryptoService.GROUP_NAME
            )
        fun signingClientId(config: CryptoLibraryConfig) =
            config.publicKeys.persistenceConfig.getString(
                DefaultConfigConsts.Kafka.CLIENT_ID_KEY,
                DefaultConfigConsts.Kafka.DefaultCryptoService.CLIENT_ID
            )
        val defaultConfig: CryptoLibraryConfig = CryptoLibraryConfigImpl(emptyMap())
        val customConfig: CryptoLibraryConfig = CryptoLibraryConfigImpl(
            mapOf(
                "defaultCryptoService" to mapOf(
                    "persistenceConfig" to mapOf(
                        DefaultConfigConsts.Kafka.GROUP_NAME_KEY to "groupName",
                        DefaultConfigConsts.Kafka.TOPIC_NAME_KEY to "keyCacheTopic",
                        DefaultConfigConsts.Kafka.CLIENT_ID_KEY to "clientId"
                    )
                ),
                "publicKeys" to mapOf(
                    "persistenceConfig" to mapOf(
                        DefaultConfigConsts.Kafka.GROUP_NAME_KEY to "groupName",
                        DefaultConfigConsts.Kafka.TOPIC_NAME_KEY to "mngCacheTopic",
                        DefaultConfigConsts.Kafka.CLIENT_ID_KEY to "clientId"
                    )

                )
            )
        )

        inline fun <reified V : IHaveTenantId, E : IHaveTenantId> KeyValuePersistence<V, E>.wait(
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

    fun createFactory(config: CryptoLibraryConfig, snapshot: (() -> Unit)? = null): KafkaKeyValuePersistenceFactory {
        if(snapshot != null) {
            snapshot()
        }
        return KafkaKeyValuePersistenceFactory(
            subscriptionFactory = subscriptionFactory,
            publisherFactory = publisherFactory,
            config = config
        )
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

    inline fun <reified V : IHaveTenantId, E : IHaveTenantId> publish(
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
}
