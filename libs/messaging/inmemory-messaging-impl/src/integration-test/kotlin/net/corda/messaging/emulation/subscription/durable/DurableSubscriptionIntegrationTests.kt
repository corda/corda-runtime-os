package net.corda.messaging.emulation.subscription.durable

import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import java.util.concurrent.ConcurrentHashMap
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

    private val numberOfMessagesToSend = 10
    private val numberOfMessagesToReplyForEachMessage = 2
    private val numberOfSubscribers = 3

    private val index = AtomicInteger(0)
    private val receivedValues = ConcurrentHashMap.newKeySet<Int?>()

    private val otherConfig = SubscriptionConfig(
        eventTopic = "durable.integration.test.other.topic",
        groupName = "durable.integration.test.other.group"
    )
    private val config = SubscriptionConfig(
        eventTopic = "durable.integration.test.topic",
        groupName = "durable.integration.test.group",
    )

    private val counter = CountDownLatch(numberOfMessagesToSend * numberOfMessagesToReplyForEachMessage)

    private fun subscription(): Subscription<Key, Value> {
        return subscriptionFactory.createDurableSubscription(
            subscriptionConfig = config,
            processor = object : DurableProcessor<Key, Value> {
                override fun onNext(events: List<Record<Key, Value>>): List<Record<*, *>> {
                    return events.flatMap {
                        (1..numberOfMessagesToReplyForEachMessage).map {
                            Record(
                                otherConfig.eventTopic,
                                Key(it.toLong()),
                                Value(index.incrementAndGet())
                            )
                        }
                    }
                }

                override val keyClass = Key::class.java
                override val valueClass = Value::class.java
            },
            partitionAssignmentListener = null
        )
    }

    private fun otherSubscription(): Subscription<Key, Value> {
        return subscriptionFactory.createDurableSubscription(
            subscriptionConfig = otherConfig,
            processor = object : DurableProcessor<Key, Value> {
                override fun onNext(events: List<Record<Key, Value>>): List<Record<*, *>> {
                    events.forEach { event ->
                        receivedValues.add(event.value?.data)
                        counter.countDown()
                    }
                    return emptyList()
                }

                override val keyClass = Key::class.java
                override val valueClass = Value::class.java
            },
            partitionAssignmentListener = null
        )
    }

    @Test
    fun `test durable subscription`() {
        val subscriptions = (1..numberOfSubscribers).flatMap {
            listOf(subscription(), otherSubscription())
        }.onEach {
            it.start()
        }

        val publisherConfig = PublisherConfig(clientId)
        val records = (1..numberOfMessagesToSend).map {
            Record(config.eventTopic, Key(0), Value(1))
        }
        publisherFactory.createPublisher(publisherConfig).use {
            it.publish(records)
        }

        counter.await(10, TimeUnit.SECONDS)

        subscriptions.forEach {
            it.stop()
        }

        assertThat(receivedValues).containsAll((1..(numberOfMessagesToSend * numberOfMessagesToReplyForEachMessage)))
    }
}
