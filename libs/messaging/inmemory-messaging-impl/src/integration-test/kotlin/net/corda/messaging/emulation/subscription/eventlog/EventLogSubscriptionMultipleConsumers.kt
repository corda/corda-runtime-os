package net.corda.messaging.emulation.subscription.eventlog

import net.corda.lifecycle.LifeCycle
import net.corda.messaging.api.processor.EventLogProcessor
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.PartitionAssignmentListener
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import java.util.Random
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

@ExtendWith(ServiceExtension::class)
class EventLogSubscriptionMultipleConsumers {

    private val random = Random()

    @InjectService(timeout = 4000)
    lateinit var subscriptionFactory: SubscriptionFactory

    @InjectService(timeout = 4000)
    lateinit var publisherFactory: PublisherFactory

    private val published = ConcurrentHashMap.newKeySet<Record<String, String>>()
    private val consumed = ConcurrentHashMap<String, MutableCollection<EventLogRecord<String, String>>>()
    private val subscriptions = ConcurrentHashMap.newKeySet<LifeCycle>()
    private val publishedLatch = CountDownLatch(numberOfPublisher)

    private val issues = CopyOnWriteArrayList<Exception>()

    private fun publish() {
        (1..numberOfPublisher).forEach { publisherId ->
            thread {
                val publisherConfig = PublisherConfig("publisher.$publisherId")
                repeat(10) {
                    val size = random.nextInt(10) + 5
                    val records = (0..size).map {
                        Record(
                            "topic.${random.nextInt(numberOfTopics) + 1}",
                            "key.${random.nextInt(100)}",
                            UUID.randomUUID().toString()
                        )
                    }
                    publisherFactory.createPublisher(publisherConfig).use {
                        it.publish(records)
                    }
                    published.addAll(records)
                    Thread.sleep(1)
                }
                publishedLatch.countDown()
            }
        }
    }
    private fun consume() {
        (1..numberOfTopics).forEach { topicId ->
            val topicName = "topic.$topicId"
            (1..numberOfConsumerGroups).forEach { groupId ->
                val groupName = "group.name.$groupId"
                val groupConsumed = consumed.computeIfAbsent(groupName) {
                    ConcurrentHashMap.newKeySet()
                }
                val processor = object : EventLogProcessor<String, String> {
                    override fun onNext(events: List<EventLogRecord<String, String>>): List<Record<*, *>> {
                        if (events.any { it.topic != topicName }) {
                            issues.add(Exception("Got the wrong topic! expecting only $topicName but got ${events.filter { it.topic != topicName }}"))
                        }
                        groupConsumed.addAll(events)
                        return emptyList()
                    }
                    override val keyClass = String::class.java
                    override val valueClass = String::class.java
                }
                val config = SubscriptionConfig(groupName = groupName, eventTopic = topicName)
                val consumerCount = random.nextInt(4) + 1
                (0..consumerCount).forEach { consumerNumber ->
                    val listener = object : PartitionAssignmentListener {
                        private val myAssignments = ConcurrentHashMap.newKeySet<Int>()
                        override fun onPartitionsUnassigned(topicPartitions: List<Pair<String, Int>>) {
                            val topics = topicPartitions.map { it.first }.toSet()
                            if (topics != setOf(topicName)) {
                                issues.add(Exception("$consumerNumber unassigned for the wrong topic - expected $topicName, got $topicPartitions"))
                            }
                            val partitions = topicPartitions.map { it.second }.toSet()
                            if ((partitions - myAssignments).isNotEmpty()) {
                                issues.add(Exception("$consumerNumber unassigned something that was never assigned. I know of $myAssignments, I got $topicPartitions"))
                            }
                            myAssignments.removeAll(partitions)
                        }

                        override fun onPartitionsAssigned(topicPartitions: List<Pair<String, Int>>) {
                            val topics = topicPartitions.map { it.first }.toSet()
                            if (topics != setOf(topicName)) {
                                issues.add(Exception("$consumerNumber assigned for the wrong topic - expected $topicName, got $topicPartitions"))
                            }
                            val partitions = topicPartitions.map { it.second }.toSet()
                            if (myAssignments.any { partitions.contains(it) }) {
                                issues.add(Exception("$consumerNumber assigned something that was never unassigned. I know of $myAssignments, I got $topicPartitions"))
                            }
                            myAssignments.addAll(partitions)
                        }
                    }
                    val subscription = subscriptionFactory.createEventLogSubscription(
                        subscriptionConfig = config,
                        processor = processor,
                        partitionAssignmentListener = listener
                    )
                    subscriptions.add(subscription)
                }
            }
        }

        subscriptions.forEach { it.start() }
    }

    @Test
    fun `test events log subscription`() {
        consume()
        publish()

        publishedLatch.await()
        val started = System.currentTimeMillis()
        while ((System.currentTimeMillis() - started) < 2000) {
            val consumeCount = consumed.values.sumOf { it.size }
            val expectedConsumed = numberOfConsumerGroups * published.size
            if (consumeCount >= expectedConsumed) {
                break
            }
            Thread.sleep(10)
        }

        subscriptions.forEach {
            it.stop()
        }

        consumed.forEach { (groupName, events) ->
            val eventRecords = events.map { Record(it.topic, it.key, it.value) }
            val missing = published - eventRecords
            if (missing.isNotEmpty()) {
                issues.add(Exception("For $groupName missing ${missing.size} for example: ${missing.take(5)}"))
            }
            val unexpected = eventRecords - published
            if (unexpected.isNotEmpty()) {
                issues.add(Exception("For $groupName unexpected ${unexpected.size} for example: ${unexpected.take(5)}"))
            }
        }

        issues.forEach {
            it.printStackTrace()
        }
        assertThat(issues).isEmpty()
    }

    companion object {
        private const val numberOfPublisher = 10
        private const val numberOfTopics = 5
        private const val numberOfConsumerGroups = 6
    }
}
