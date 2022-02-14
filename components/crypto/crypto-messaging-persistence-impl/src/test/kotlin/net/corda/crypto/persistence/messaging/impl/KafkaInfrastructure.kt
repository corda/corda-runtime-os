package net.corda.crypto.persistence.messaging.impl

import com.typesafe.config.ConfigFactory
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.persistence.KeyValuePersistence
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.TimerEvent
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
import net.corda.schema.configuration.ConfigKeys.BOOT_CONFIG
import net.corda.schema.configuration.ConfigKeys.CRYPTO_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
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
                if (value != null) {
                    return value
                }
                Thread.sleep(retryDelay.toMillis())
            } while (Instant.now() < end)
            fail("Failed to wait for '$key'")
        }
    }

    private val emptyConfig = SmartConfigFactory.create(ConfigFactory.empty()).create(ConfigFactory.empty())
    private val topicService: TopicService = TopicServiceImpl()
    private val rpcTopicService: RPCTopicService = RPCTopicServiceImpl()
    private var coordinator: LifecycleCoordinator? = null
    private val registrationHandle: AutoCloseable = mock()
    private val coordinatorFactory: LifecycleCoordinatorFactory = mock {
        on { createCoordinator(any(), any()) } doAnswer {
            if(coordinator == null) {
                coordinator = TestCoordinator(
                    name = it.getArgument(0, LifecycleCoordinatorName::class.java),
                    handler = it.getArgument(1, LifecycleEventHandler::class.java)
                )
            }
            coordinator
        }
    }
    private val configurationReadService: ConfigurationReadService = mock {
        on { registerForUpdates(any()) } doReturn registrationHandle
        on { registerComponentForUpdates(any(), any()) } doReturn registrationHandle
    }
    val subscriptionFactory: SubscriptionFactory =
        InMemSubscriptionFactory(topicService, rpcTopicService, coordinatorFactory)
    val publisherFactory: PublisherFactory =
        CordaPublisherFactory(topicService, rpcTopicService, coordinatorFactory)

    fun createSigningKeysPersistenceProvider(
        snapshot: (() -> Unit)? = null
    ): MessagingSigningKeysPersistenceProvider {
        if (snapshot != null) {
            snapshot()
        }
        return MessagingSigningKeysPersistenceProvider(
            coordinatorFactory,
            subscriptionFactory,
            publisherFactory,
            configurationReadService
        ).also {
            it.start()
            coordinator?.postEvent(
                RegistrationStatusChangeEvent(
                    registration = mock(),
                    status = LifecycleStatus.UP
                )
            )
            coordinator?.postEvent(
                ConfigChangedEvent(
                    setOf(CRYPTO_CONFIG, BOOT_CONFIG, MESSAGING_CONFIG),
                    mapOf(
                        CRYPTO_CONFIG to emptyConfig,
                        BOOT_CONFIG to emptyConfig,
                        MESSAGING_CONFIG to emptyConfig
                    )
                )
            )
        }
    }

    fun createSoftPersistenceProvider(
        snapshot: (() -> Unit)? = null
    ): MessagingSoftKeysPersistenceProvider {
        if (snapshot != null) {
            snapshot()
        }
        return MessagingSoftKeysPersistenceProvider(
            coordinatorFactory,
            subscriptionFactory,
            publisherFactory,
            configurationReadService
        ).also {
            it.start()
            coordinator?.postEvent(
                RegistrationStatusChangeEvent(
                    registration = mock(),
                    status = LifecycleStatus.UP
                )
            )
            coordinator?.postEvent(
                ConfigChangedEvent(
                    setOf(CRYPTO_CONFIG, BOOT_CONFIG, MESSAGING_CONFIG),
                    mapOf(
                        CRYPTO_CONFIG to emptyConfig,
                        BOOT_CONFIG to emptyConfig,
                        MESSAGING_CONFIG to emptyConfig
                    )
                )
            )
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

    private class TestCoordinator(
        override val name: LifecycleCoordinatorName,
        private val handler: LifecycleEventHandler
    ) : LifecycleCoordinator {
        override var isRunning: Boolean = false

        override fun start() {
            isRunning = true
        }

        override fun stop() {
            isRunning = false
        }

        override fun postEvent(event: LifecycleEvent) {
            handler.processEvent(event, this)
        }

        override fun setTimer(key: String, delay: Long, onTime: (String) -> TimerEvent) {
        }

        override fun cancelTimer(key: String) {
        }

        override var status: LifecycleStatus = LifecycleStatus.DOWN

        override fun updateStatus(newStatus: LifecycleStatus, reason: String) {
            status = newStatus
        }

        override fun postCustomEventToFollowers(eventPayload: Any) {
        }

        override fun followStatusChanges(coordinators: Set<LifecycleCoordinator>): RegistrationHandle {
            return mock()
        }

        override fun followStatusChangesByName(coordinatorNames: Set<LifecycleCoordinatorName>): RegistrationHandle {
            return mock()
        }

        override val isClosed: Boolean = false

        override fun close() {
        }
    }
}