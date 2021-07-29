package net.corda.p2p.linkmanager.delivery

import net.corda.lifecycle.Lifecycle
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.p2p.app.AppMessage
import net.corda.p2p.linkmanager.LinkManager
import net.corda.p2p.schema.Schema
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class DeliveryTracker(
    flowMessageReplayPeriod: Long,
    publisherFactory: PublisherFactory,
    subscriptionFactory: SubscriptionFactory,
    messageForwarder: LinkManager.OutboundMessageProcessor
    ): Lifecycle {

    @Volatile
    private var running = false
    private val startStopLock = ReentrantLock()

    private val messageReplayer = MessageReplayer(publisherFactory, subscriptionFactory, messageForwarder)
    private val replayManager = ReplayManager(flowMessageReplayPeriod, messageReplayer::replayMessage)
    private val highWaterMarkTracker = HighWaterMarkTracker(
        replayManager::addForReplay,
        replayManager::removeFromReplay
    )

    override val isRunning: Boolean
        get() = running

    override fun start() {
        startStopLock.withLock {
            if (!isRunning) {
                messageReplayer.start()
                replayManager.start()
            }
        }
    }

    override fun stop() {
        if (isRunning) {
            startStopLock.withLock {
                messageReplayer.stop()
                replayManager.stop()
            }
        }
    }

    /**
     * It is thread safe to call [HighWaterMarkTracker.removePartition] and [HighWaterMarkTracker.addPartition] in one thread and
     * [HighWaterMarkTracker.processSentMarker] and [HighWaterMarkTracker.processReceivedMarker] in another. It is not thread safe
     * to call [HighWaterMarkTracker.processSentMarker]/[HighWaterMarkTracker.processReceivedMarker] simultaneously in different
     * threads for the same marker partition.
     */
    class HighWaterMarkTracker(
        private val processSentMarker: (messageId: String, messagePosition: PositionInTopic) -> Unit,
        private val processReceivedMarker: (messageId: String) -> Unit
    ) {

        private var logger = LoggerFactory.getLogger(this::class.java.name)

        data class PositionInTopic(val partition: Partition, val offset: Offset)

        /**
         * [committedOffset] the current committed high water mark offset.
         * [pendingOffsets] set of offsets corresponding to markers that have been processed, but are still above the high watermark.
         */
        data class TopicTrackingInfo(var committedOffset: Offset, val pendingOffsets: TreeSet<Offset>)

        private val perTopicTracker = ConcurrentHashMap<Partition, TopicTrackingInfo>()

        /**
         * The position of the start marker offset for a specific messageId.
         */
        private val startMarkerOffsets = ConcurrentHashMap<String, Offset>()

        fun addPartition(partition: Partition, committedOffset: Offset) {
            perTopicTracker[partition] = TopicTrackingInfo(committedOffset, TreeSet<Offset>())
        }

        fun removePartition(partition: Partition) {
            perTopicTracker.remove(partition)
        }

        fun processSentMarker(sentMarkerPosition: PositionInTopic, messageId: String, messagePosition: PositionInTopic) {
            startMarkerOffsets[messageId] = sentMarkerPosition.offset
            processSentMarker(messageId, messagePosition)
        }

        fun processReceivedMarker(receivedMarkerPosition: PositionInTopic, messageId: String): Offset? {
            val tracker = perTopicTracker[receivedMarkerPosition.partition]
            if (tracker == null) {
                logger.error("Received offset for partition ${receivedMarkerPosition.partition} which we are not subscribed to." +
                        " The offset was not be committed.")
                return null
            }

            tracker.pendingOffsets.add(receivedMarkerPosition.offset)

            val startMarkerOffset = startMarkerOffsets[messageId]
            if (startMarkerOffset == null) {
                logger.warn("Received a received marker for a message where there is no SentMarker.")
            } else {
                tracker.pendingOffsets.add(startMarkerOffset)
            }

            var newCommittedOffset = tracker.committedOffset
            while (tracker.pendingOffsets.contains(newCommittedOffset + 1)) {
                tracker.pendingOffsets.remove(newCommittedOffset)
                newCommittedOffset++
            }
            val newOffset = if (newCommittedOffset > tracker.committedOffset) {
                tracker.committedOffset = newCommittedOffset
                newCommittedOffset
            } else {
                null
            }
            processReceivedMarker(messageId)
            return newOffset
        }
    }

    class MessageReplayer(
        publisherFactory: PublisherFactory,
        subscriptionFactory: SubscriptionFactory,
        private val messageForwarder: LinkManager.OutboundMessageProcessor
    ): Lifecycle {

        companion object {
            const val MESSAGE_REPLAYER_CLIENT_ID = "message-replayer-client"
            const val MESSAGE_REPLAYER_SUBSCRIPTION_ID = "message-replayer-subscription"
        }

        private val config = PublisherConfig(MESSAGE_REPLAYER_CLIENT_ID, null)
        private val publisher = publisherFactory.createPublisher(config)
        private val subscriptionConfig = SubscriptionConfig(MESSAGE_REPLAYER_SUBSCRIPTION_ID, Schema.P2P_OUT_TOPIC)
        private val subscription = subscriptionFactory.createRandomAccessSubscription(
            subscriptionConfig,
            keyClass = ByteBuffer::class.java,
            valueClass = AppMessage::class.java
        )
        private var logger = LoggerFactory.getLogger(this::class.java.name)

        @Volatile
        private var running = false
        private val startStopLock = ReentrantLock()
        override val isRunning: Boolean
            get() = running

        override fun start() {
            startStopLock.withLock {
                if (!isRunning) {
                    subscription.start()
                    publisher.start()
                }
                running = true
            }
        }

        override fun stop() {
            startStopLock.withLock {
                if (isRunning) {
                    subscription.stop()
                }
                running = false
            }
        }

        fun replayMessage(messagePosition: HighWaterMarkTracker.PositionInTopic) {
            val message = subscription.getRecord(messagePosition.partition, messagePosition.offset)
            if (message == null) {
                logger.error("Could not find a message for replay at partition ${messagePosition.partition} and offset " +
                        "${messagePosition.offset} in topic ${Schema.P2P_OUT_TOPIC}. The message was not replayed.")
                return
            }
            val eventLogRecord = message.toEventLogRecord(messagePosition)
            val records = messageForwarder.processEvent(eventLogRecord, false)
            publisher.publish(records)
        }

        private fun <K: Any, V : Any> Record<K, V>.toEventLogRecord(positionInTopic: HighWaterMarkTracker.PositionInTopic): EventLogRecord<K, V> {
            return EventLogRecord(topic, key, value, positionInTopic.partition, positionInTopic.offset)
        }
    }
}

typealias Partition = Int
typealias Offset = Long