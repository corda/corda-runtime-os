package net.corda.messaging.emulation.subscription.durable

import net.corda.messaging.api.processor.DurableProcessor
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@ExtendWith(ServiceExtension::class)
class DurableSubscriptionIntegrationTests {

    data class Value(val data: Int)
    data class Key(val data: Long)

    private val clientId = "testId"

    @InjectService(timeout = 4000)
    lateinit var subscriptionFactory: SubscriptionFactory

    @InjectService(timeout = 4000)
    lateinit var publisherFactory: PublisherFactory

    // each topic will send 3 times more events than the previous one.
    val counter = CountDownLatch(1 + 3 + 9 + 27)

    inner class Subscription(private val index: Int) {
        val config = SubscriptionConfig(
            eventTopic = "durable.integration.test.topic.$index",
            groupName = "durable.integration.test.group.$index",
        )
        val receivedEvents = AtomicInteger(0)
        private val subscriptions by lazy {
            (1..4).map {
                subscriptionFactory.createDurableSubscription(
                    subscriptionConfig = config,
                    processor = object : DurableProcessor<Key, Value> {
                        override fun onNext(events: List<Record<Key, Value>>): List<Record<*, *>> {
                            return events.flatMap {
                                receivedEvents.incrementAndGet()
                                counter.countDown()
                                (1..3).map { Record("durable.integration.test.topic.${index + 1}", Key(it.toLong()), Value(3)) }
                            }
                        }

                        override val keyClass = Key::class.java
                        override val valueClass = Value::class.java
                    },
                    partitionAssignmentListener = null
                )
            }
        }

        fun start() {
            subscriptions.forEach {
                it.start()
            }
        }
        fun stop() {
            subscriptions.forEach {
                it.stop()
            }
        }
    }

    private val subscriptions by lazy {
        (1..4).map { it -> Subscription(it).also { it.start() } }
    }

    @Test
    fun `test durable subscription`() {
        subscriptions.forEach {
            it.start()
        }

        val publisherConfig = PublisherConfig(clientId)
        val records = listOf(Record(subscriptions[0].config.eventTopic, Key(0), Value(1)))
        publisherFactory.createPublisher(publisherConfig).use {
            it.publish(records)
        }

        counter.await(10, TimeUnit.SECONDS)

        subscriptions.forEach {
            it.stop()
        }

        assertThat(subscriptions.map { it.receivedEvents.get() }).isEqualTo(listOf(1, 3, 9, 27))
    }
}
