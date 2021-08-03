package net.corda.p2p.gateway

import com.typesafe.config.Config
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.processor.EventLogProcessor
import net.corda.messaging.api.processor.PubSubProcessor
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.PartitionAssignmentListener
import net.corda.messaging.api.subscription.RandomAccessSubscription
import net.corda.messaging.api.subscription.StateAndEventSubscription
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.messaging.emulation.topic.model.OffsetStrategy
import net.corda.messaging.emulation.topic.model.RecordMetadata
import net.corda.messaging.emulation.topic.service.TopicService
import net.corda.v5.base.internal.uncheckedCast
import rx.Observable
import rx.subjects.PublishSubject
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Basic stubbed implementation of a [TopicService] for testing. Allows inserting and retrieving [Record] objects from named topics
 * The topics are append-only and will always be read completely.
 */
class TopicServiceStub : TopicService {
    private val topics = ConcurrentHashMap<String, List<Record<*, *>>>()

    override fun addRecords(records: List<Record<*, *>>) {
        records.forEach {
            topics.merge(it.topic, listOf(it)) { x, y -> x + y }
        }

        // Notify subscribers that new records have been published
        topics.keys.forEach {
            _onPublish.onNext(topics[it])
        }
    }

    override fun getRecords(
        topicName: String,
        consumerGroup: String,
        numberOfRecords: Int,
        autoCommitOffset: Boolean
    ): List<RecordMetadata> {
        return topics.getOrDefault(topicName, emptyList()).map { RecordMetadata(0, it) }
    }

    override fun commitOffset(topicName: String, consumerGroup: String, offset: Long) = Unit

    override fun subscribe(topicName: String, consumerGroup: String, offsetStrategy: OffsetStrategy) = Unit

    private val _onPublish = PublishSubject.create<List<Record<*, *>>>().toSerialized()
    val onPublish: Observable<List<Record<*, *>>>
        get() = _onPublish
}

/**
 * Basic stubbed implementation of a [Subscription] for testing. Allows using an [EventLogProcessor] to read [Record] objects
 * via a [TopicService]. Upon start, all messages currently in the topic are fed into the processor exactly once. Updates
 * are no longer published.
 */
class EventLogSubscriptionStub<K: Any, V: Any>(
    private val config: SubscriptionConfig,
    private val processor: EventLogProcessor<K, V>,
    private val topicService: TopicService,
) : Subscription<K, V> {
    @Volatile
    private var running = false
    private val lock = ReentrantLock()

    override fun stop() {
        lock.withLock { running = false }
    }

    override fun start() {
        lock.withLock {
            running = true
            val records = topicService.getRecords(config.eventTopic, config.groupName, -1).map {
                EventLogRecord<K, V>(it.record.topic,
                    uncheckedCast(it.record.key), uncheckedCast(it.record.value), -1, -1L) }
            if (records.isNotEmpty())  {
                processor.onNext(records)
            }
        }
    }

    override val isRunning: Boolean
        get() = running

}

/**
 * Basic stubbed implementation for [SubscriptionFactory]. Currently used to inject an [EventLogSubscriptionStub] in the
 * Gateway.
 */
class SubscriptionFactoryStub(private val topicService: TopicService) : SubscriptionFactory {
    override fun <K : Any, V : Any> createPubSubSubscription(
        subscriptionConfig: SubscriptionConfig,
        processor: PubSubProcessor<K, V>,
        executor: ExecutorService?,
        nodeConfig: Config
    ): Subscription<K, V> {
        TODO("Not yet implemented")
    }

    override fun <K : Any, V : Any> createDurableSubscription(
        subscriptionConfig: SubscriptionConfig,
        processor: DurableProcessor<K, V>,
        nodeConfig: Config,
        partitionAssignmentListener: PartitionAssignmentListener?
    ): Subscription<K, V> {
        TODO("Not yet implemented")
    }

    override fun <K : Any, V : Any> createCompactedSubscription(
        subscriptionConfig: SubscriptionConfig,
        processor: CompactedProcessor<K, V>,
        nodeConfig: Config
    ): CompactedSubscription<K, V> {
        TODO("Not yet implemented")
    }

    override fun <K : Any, S : Any, E : Any> createStateAndEventSubscription(
        subscriptionConfig: SubscriptionConfig,
        processor: StateAndEventProcessor<K, S, E>,
        nodeConfig: Config
    ): StateAndEventSubscription<K, S, E> {
        TODO("Not yet implemented")
    }

    override fun <K : Any, V : Any> createEventLogSubscription(
        subscriptionConfig: SubscriptionConfig,
        processor: EventLogProcessor<K, V>,
        nodeConfig: Config,
        partitionAssignmentListener: PartitionAssignmentListener?
    ): Subscription<K, V> {
        return EventLogSubscriptionStub(subscriptionConfig, processor, topicService)
    }

    override fun <K : Any, V : Any> createRandomAccessSubscription(
        subscriptionConfig: SubscriptionConfig,
        nodeConfig: Config
    ): RandomAccessSubscription<K, V> {
        TODO("Not yet implemented")
    }
}

/**
 * Basic implementation of a [Publisher].
 */
class PublisherStub(private val topicService: TopicService) : Publisher {
    override fun publishToPartition(records: List<Pair<Int, Record<*, *>>>): List<CompletableFuture<Unit>> {
        return emptyList()
    }

    override fun publish(records: List<Record<*, *>>): List<CompletableFuture<Unit>> {
        topicService.addRecords(records)
        return emptyList()
    }

    override fun close() = Unit

}

/**
 * Basic implementation of a [PublisherFactory]
 */
class PublisherFactoryStub(private val topicService: TopicService) : PublisherFactory {
    override fun createPublisher(publisherConfig: PublisherConfig, nodeConfig: Config): Publisher {
        return PublisherStub(topicService)
    }
}

