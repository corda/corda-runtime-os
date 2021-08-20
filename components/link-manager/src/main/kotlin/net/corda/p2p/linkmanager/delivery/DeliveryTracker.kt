package net.corda.p2p.linkmanager.delivery

import net.corda.lifecycle.Lifecycle
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import net.corda.messaging.api.processor.StateAndEventProcessorWithReassignment
import net.corda.messaging.api.processor.StateAndEventProcessorWithReassignment.Response
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.StateAndEventSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.p2p.AuthenticatedMessageDeliveryState
import net.corda.p2p.app.AppMessage
import net.corda.p2p.markers.AppMessageMarker
import net.corda.p2p.markers.LinkManagerReceivedMarker
import net.corda.p2p.markers.LinkManagerSentMarker
import net.corda.p2p.schema.Schema
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

//TODO: Delete this when StateAndEventSubscriptionAPI is updated
interface StateAndEventSubscriptionWithReassignmentFactory {
    fun <K : Any, S : Any, E : Any> createStateAndEventSubscription(
        subscriptionConfig: SubscriptionConfig,
        processor: StateAndEventProcessorWithReassignment<K, S, E>,
        nodeConfig: Config = ConfigFactory.empty()
    ) : StateAndEventSubscription<K, S, E>
}

class DeliveryTracker(
    flowMessageReplayPeriod: Long,
    publisherFactory: PublisherFactory,
    subscriptionFactory: SubscriptionFactory,
    subscriptionFactoryWithReassignment: StateAndEventSubscriptionWithReassignmentFactory,
    processFlowMessage: (event: EventLogRecord<ByteBuffer, AppMessage>) -> List<Record<String, *>>
): Lifecycle {

    @Volatile
    private var running = false
    private val startStopLock = ReentrantLock()

    private val flowMessageReplayer = FlowMessageReplayer(publisherFactory, subscriptionFactory, processFlowMessage)
    private val replayManager = ReplayScheduler(flowMessageReplayPeriod, flowMessageReplayer::replayMessage)

    //TODO: Use the subscriptionFactory instead to create a message tracker
    private val messageTracker = subscriptionFactoryWithReassignment.createStateAndEventSubscription(
        SubscriptionConfig("message-tracker-group", Schema.P2P_OUT_MARKERS),
        MessageTracker(replayManager)
    )

    data class PositionInTopic(val partition: Partition, val offset: Offset)

    override val isRunning: Boolean
        get() = running

    override fun start() {
        startStopLock.withLock {
            if (!isRunning) {
                flowMessageReplayer.start()
                replayManager.start()
                messageTracker.start()
                running = true
            }
        }
    }

    override fun stop() {
        if (isRunning) {
            startStopLock.withLock {
                flowMessageReplayer.stop()
                replayManager.stop()
                messageTracker.stop()
                running = false
            }
        }
    }

    class FlowMessageReplayer(
        publisherFactory: PublisherFactory,
        subscriptionFactory: SubscriptionFactory,
        private val processFlowMessage: (event: EventLogRecord<ByteBuffer, AppMessage>) -> List<Record<String, *>>
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

        fun replayMessage(messagePosition: PositionInTopic) {
            val message = subscription.getRecord(messagePosition.partition, messagePosition.offset)
            if (message == null) {
                logger.error("Could not find a message for replay at partition ${messagePosition.partition} and offset " +
                        "${messagePosition.offset} in topic ${Schema.P2P_OUT_TOPIC}. The message was not replayed.")
                return
            }
            val eventLogRecord = message.toEventLogRecord(messagePosition)
            val records = processFlowMessage(eventLogRecord)
            publisher.publish(records)
        }

        private fun <K: Any, V : Any> Record<K, V>.toEventLogRecord(positionInTopic: PositionInTopic): EventLogRecord<K, V> {
            return EventLogRecord(topic, key, value, positionInTopic.partition, positionInTopic.offset)
        }
    }

    class MessageTracker(
        private val replayScheduler: ReplayScheduler<PositionInTopic>
    ) : StateAndEventProcessorWithReassignment<String, AuthenticatedMessageDeliveryState, AppMessageMarker> {

        override fun onNext(
            state: AuthenticatedMessageDeliveryState?,
            event: Record<String, AppMessageMarker>
        ): Response<AuthenticatedMessageDeliveryState> {
            val marker = event.value ?: return respond(null)
            val markerType = marker.marker
            val timestamp = marker.timestamp
            return when (markerType) {
                is LinkManagerSentMarker -> Response(
                    AuthenticatedMessageDeliveryState(
                        markerType.partition,
                        markerType.offset,
                        timestamp
                    ), emptyList()
                )
                is LinkManagerReceivedMarker -> Response(null, emptyList())
                else -> respond(state)
            }
        }

        private fun respond(state: AuthenticatedMessageDeliveryState?): Response<AuthenticatedMessageDeliveryState> {
            return Response(state, emptyList())
        }

        override fun onCommit(states: Map<String, AuthenticatedMessageDeliveryState?>) {
            for ((key, state) in states) {
                if (state != null) {
                    replayScheduler.addForReplay(state.timestamp, key, PositionInTopic(state.partition.toInt(), state.offset))
                } else {
                    replayScheduler.removeFromReplay(key)
                }
            }
        }

        override fun onPartitionsRevoked(states: Map<String, AuthenticatedMessageDeliveryState>) {
            for ((key, _) in states) {
                replayScheduler.removeFromReplay(key)
            }
        }

        override fun onPartitionsAssigned(states: Map<String, AuthenticatedMessageDeliveryState>) {
            for ((key, state) in states) {
                replayScheduler.addForReplay(state.timestamp, key, PositionInTopic(state.partition.toInt(), state.offset))
            }
        }

        override val keyClass = String::class.java
        override val stateValueClass =  AuthenticatedMessageDeliveryState::class.java
        override val eventValueClass = AppMessageMarker::class.java
    }
}

typealias Partition = Int
typealias Offset = Long