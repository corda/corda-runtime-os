package net.corda.messaging.emulation.subscription.compacted

import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@ExtendWith(ServiceExtension::class)
class InMemoryCompactedSubscriptionIntegrationTest {

    private data class Event(val name: String, val id: Int)

    private val group = "net.corda.messaging.emulation.subscription.compacted"
    private val topic = "compacted.test"
    private val clientId = "testId"

    @InjectService(timeout = 4000)
    lateinit var subscriptionFactory: SubscriptionFactory

    @InjectService(timeout = 4000)
    lateinit var publisherFactory: PublisherFactory

    private data class NextDetails(
        val newRecord: Record<String, Event>,
        val oldValue: Event?,
        val currentData: Map<String, Event>
    )
    private val processed = CopyOnWriteArrayList<NextDetails>()
    private val gotSnapshots = CopyOnWriteArrayList<Map<String, Event>>()

    private val waitForProcessed = AtomicReference<CountDownLatch>(null)

    private val subscription by lazy {
        val config = SubscriptionConfig(groupName = group, eventTopic = topic)
        val processor = object : CompactedProcessor<String, Event> {

            override val keyClass = String::class.java
            override val valueClass = Event::class.java
            override fun onSnapshot(currentData: Map<String, Event>) {
                gotSnapshots.add(currentData)
            }

            override fun onNext(newRecord: Record<String, Event>, oldValue: Event?, currentData: Map<String, Event>) {
                processed.add(NextDetails(newRecord, oldValue, currentData))
            }
        }
        subscriptionFactory.createCompactedSubscription(
            subscriptionConfig = config,
            processor = processor,
        )
    }

    private fun publish(vararg records: Record<Any, Any>) {
        val publisherConfig = PublisherConfig(clientId)
        publisherFactory.createPublisher(publisherConfig).use {
            it.publish(records.toList())
        }
    }

    @Test
    fun `test compact subscription`() {
        publish(
            Record(topic, "key1", Event("one", 1)),
            Record(topic, "key1", Event("one", 2)),
            Record(topic, "key1", Event("one", 3)),
            Record(topic, "key2", Event("two", 2)),
        )

        assertThat(subscription.isRunning).isFalse

        // Start the subscription
        subscription.start()
        assertThat(subscription.isRunning).isTrue
        assertThat(processed).isEmpty()

        // Let the other thread start their loops
        Thread.sleep(40)
        assertThat(gotSnapshots)
            .hasSize(1)
            .contains(
                mapOf(
                    "key1" to Event("one", 3),
                    "key2" to Event("two", 2)
                )
            )

        // Publish a few events
        waitForProcessed.set(CountDownLatch(5))
        publish(
            Record(topic, "key1", Event("one", 1)),
            Record("another.$topic", "key4", Event("four", 4)),
            Record(topic, "key2", Event("two", 2)),
            Record(topic, "key3", Event("three", 3)),
            Record(topic, "key2", Event("two", 4)),
            Record(topic, "key1", null),
        )

        // Wait for the events
        waitForProcessed.get().await(1, TimeUnit.SECONDS)
        assertThat(processed.map { it.newRecord }).contains(
            Record(topic, "key1", Event("one", 1)),
            Record(topic, "key2", Event("two", 2)),
            Record(topic, "key3", Event("three", 3)),
            Record(topic, "key2", Event("two", 4)),
            Record(topic, "key1", null),
        ).hasSize(5)

        processed.clear()
        waitForProcessed.set(CountDownLatch(3))
        publish(
            Record(topic, "key3", null),
            Record(topic, "key2", Event("two", 100)),
            Record(topic, "key4", Event("four", 400)),
        )

        waitForProcessed.get().await(1, TimeUnit.SECONDS)
        assertThat(processed.map { it.oldValue }).contains(
            Event("three", 3),
            Event("two", 4),
            null
        ).hasSize(3)

        processed.clear()
        waitForProcessed.set(CountDownLatch(1))
        publish(
            Record(topic, "key5", Event("five", -5)),
        )

        waitForProcessed.get().await(1, TimeUnit.SECONDS)
        assertThat(processed.map { it.currentData }).hasSize(1).contains(
            mapOf(
                "key2" to Event("two", 100),
                "key4" to Event("four", 400),
                "key5" to Event("five", -5),
            )
        )

        // Stop the subscriber
        subscription.stop()
        assertThat(subscription.isRunning).isFalse
    }
}
