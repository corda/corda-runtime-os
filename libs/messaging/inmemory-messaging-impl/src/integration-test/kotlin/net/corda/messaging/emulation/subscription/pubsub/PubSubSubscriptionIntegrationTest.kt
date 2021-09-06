package net.corda.messaging.emulation.subscription.pubsub

import net.corda.messaging.api.processor.PubSubProcessor
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@ExtendWith(ServiceExtension::class)
class PubSubSubscriptionIntegrationTest {

    private data class Event(val name: String, val id: Int)

    private val group = "net.corda.messaging.emulation.subscription.pubsub"
    private val topic = "pubsub.test"
    private val clientId = "testId"

    @InjectService(timeout = 4000)
    lateinit var subscriptionFactory: SubscriptionFactory

    @InjectService(timeout = 4000)
    lateinit var publisherFactory: PublisherFactory

    private val processed = ConcurrentHashMap.newKeySet<Record<String, Event>>()

    private val waitForProcessed = AtomicReference<CountDownLatch>(null)

    private val executor = Executors.newFixedThreadPool(3) {
        Thread(it).also {
            it.isDaemon = true
        }
    }

    private val subscription by lazy {
        val config = SubscriptionConfig(groupName = group, eventTopic = topic)
        val processor = object : PubSubProcessor<String, Event> {

            override val keyClass = String::class.java
            override val valueClass = Event::class.java
            override fun onNext(event: Record<String, Event>) {
                processed.add(event)
                waitForProcessed.get().countDown()
            }
        }
        subscriptionFactory.createPubSubSubscription(
            subscriptionConfig = config,
            processor = processor,
            executor = executor
        )
    }

    private fun publish(vararg records: Record<Any, Any>) {
        val publisherConfig = PublisherConfig(clientId)
        publisherFactory.createPublisher(publisherConfig).use {
            it.publish(records.toList())
        }
    }

    @AfterEach
    fun cleanup() {
        executor.shutdown()
    }

    @Test
    fun `test pub sub subscription`() {
        publish(
            Record(topic, "key0", Event("one", 1)),
        )

        assertThat(subscription.isRunning).isFalse

        // Start the subscription
        subscription.start()
        assertThat(subscription.isRunning).isTrue
        assertThat(processed).isEmpty()

        // Let the other thread start their loops
        Thread.sleep(40)

        // Publish a few events
        waitForProcessed.set(CountDownLatch(3))
        publish(
            Record(topic, "key1", Event("one", 1)),
            Record("another.$topic", "key4", Event("four", 4)),
            Record(topic, "key2", Event("two", 2)),
            Record(topic, "key3", Event("three", 3)),
        )

        // Wait for the events
        waitForProcessed.get().await(1, TimeUnit.SECONDS)
        assertThat(processed).contains(
            Record(topic, "key1", Event("one", 1)),
            Record(topic, "key2", Event("two", 2)),
            Record(topic, "key3", Event("three", 3)),
        ).hasSize(3)

        // Stop the subscription
        subscription.stop()
        assertThat(subscription.isRunning).isFalse

        // Publish an event
        processed.clear()
        publish(Record(topic, "key4", Event("four", 4)))

        // Verify it was not processed
        Thread.sleep(1000)
        assertThat(processed).isEmpty()

        // Restart the subscription
        subscription.start()
        assertThat(subscription.isRunning).isTrue
        assertThat(processed).isEmpty()

        // Publish a few events
        waitForProcessed.set(CountDownLatch(3))
        publish(
            Record("another $topic", "keya", Event("five", 5)),
            Record(topic, "key5", Event("five", 5)),
            Record(topic, 12, Event("five", 5)),
            Record(topic, "keyb", 31),
            Record(topic, "key6", Event("six", 6)),
        )

        // Wait for the events
        waitForProcessed.get().await(1, TimeUnit.SECONDS)
        assertThat(processed.map { it.key })
            .hasSize(3)
            .contains(
                "key4",
                "key5",
                "key6"
            )

        // Stop the subscriber
        subscription.stop()
        assertThat(subscription.isRunning).isFalse
    }
}
