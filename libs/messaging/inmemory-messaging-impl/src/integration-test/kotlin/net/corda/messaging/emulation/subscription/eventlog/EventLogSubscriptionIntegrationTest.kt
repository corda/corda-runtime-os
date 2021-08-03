package net.corda.messaging.emulation.subscription.eventlog

import net.corda.messaging.api.processor.EventLogProcessor
import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.PartitionAssignmentListener
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension

@ExtendWith(ServiceExtension::class)
class EventLogSubscriptionIntegrationTest {
    private data class Event(val name: String, val id: Int)

    private val group = "net.corda.messaging.emulation.subscription.eventlog"
    private val topic = "eventlog.test"
    private val config = SubscriptionConfig(groupName = group, eventTopic = topic)

    private val processed = mutableListOf<List<EventLogRecord<String, Event>>>()
    private val processor = object : EventLogProcessor<String, Event> {
        override fun onNext(events: List<EventLogRecord<String, Event>>): List<Record<*, *>> {
            processed.add(events)
            return emptyList()
        }

        override val keyClass = String::class.java
        override val valueClass = Event::class.java
    }

    private val assigned = mutableMapOf<String, Int>()

    private val partitionAssignmentListener = object : PartitionAssignmentListener {
        override fun onPartitionsUnassigned(topicPartitions: List<Pair<String, Int>>) {
            assigned.putAll(topicPartitions.toMap())
        }

        override fun onPartitionsAssigned(topicPartitions: List<Pair<String, Int>>) {
        }
    }

    @InjectService(timeout = 4000)
    private lateinit var subscriptionFactory: SubscriptionFactory

    @Test
    fun `test events log subscription`() {
        println("QQQ subscriptionFactory = $subscriptionFactory")
        val subscription = subscriptionFactory.createEventLogSubscription(
            subscriptionConfig = config,
            processor = processor,
            partitionAssignmentListener = partitionAssignmentListener
        )

        println("QQQ subscription = $subscription")
        println("QQQ isRunning = ${subscription.isRunning}")
        subscription.start()
        println("QQQ isRunning = ${subscription.isRunning}")
        subscription.stop()
        println("QQQ isRunning = ${subscription.isRunning}")
    }
}
