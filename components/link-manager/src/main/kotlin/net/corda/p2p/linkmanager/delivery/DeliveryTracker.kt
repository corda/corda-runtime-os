package net.corda.p2p.linkmanager.delivery

import net.corda.lifecycle.Lifecycle
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.processor.StateAndEventProcessor.Response
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.messaging.api.subscription.listener.StateAndEventListener
import net.corda.p2p.AuthenticatedMessageAndKey
import net.corda.p2p.AuthenticatedMessageDeliveryState
import net.corda.p2p.markers.AppMessageMarker
import net.corda.p2p.markers.LinkManagerReceivedMarker
import net.corda.p2p.markers.LinkManagerSentMarker
import net.corda.p2p.schema.Schema
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.withLock
import kotlin.concurrent.write

class DeliveryTracker(
    flowMessageReplayPeriod: Duration,
    publisherFactory: PublisherFactory,
    subscriptionFactory: SubscriptionFactory,
    processAuthenticatedMessage: (message: AuthenticatedMessageAndKey) -> List<Record<String, *>>
): Lifecycle {

    @Volatile
    private var running = false
    private val startStopLock = ReentrantLock()

    private val appMessageReplayer = AppMessageReplayer(publisherFactory, processAuthenticatedMessage)
    private val replayScheduler = ReplayScheduler(flowMessageReplayPeriod, appMessageReplayer::replayMessage)

    private val messageTracker = MessageTracker(replayScheduler)

    private val messageTrackerSubscription = subscriptionFactory.createStateAndEventSubscription(
        SubscriptionConfig("message-tracker-group", Schema.P2P_OUT_MARKERS),
        processor = messageTracker.processor,
        stateAndEventListener = messageTracker.listener
    )

    override val isRunning: Boolean
        get() = running

    override fun start() {
        startStopLock.withLock {
            if (!isRunning) {
                appMessageReplayer.start()
                replayScheduler.start()
                messageTrackerSubscription.start()
                running = true
            }
        }
    }

    override fun stop() {
        startStopLock.withLock {
            if (isRunning) {
                appMessageReplayer.stop()
                replayScheduler.stop()
                messageTrackerSubscription.stop()
                running = false
            }
        }
    }

    private class AppMessageReplayer(
        publisherFactory: PublisherFactory,
        private val processAuthenticatedMessage: (message: AuthenticatedMessageAndKey) -> List<Record<String, *>>
    ): Lifecycle {

        companion object {
            const val MESSAGE_REPLAYER_CLIENT_ID = "message-replayer-client"
        }

        private val config = PublisherConfig(MESSAGE_REPLAYER_CLIENT_ID, null)
        private val publisher = publisherFactory.createPublisher(config)

        @Volatile
        private var running = false
        private val startStopLock = ReentrantReadWriteLock()
        override val isRunning: Boolean
            get() = running

        override fun start() {
            startStopLock.write {
                if (!isRunning) {
                    publisher.start()
                }
                running = true
            }
        }

        override fun stop() {
            startStopLock.write {
              if (running) {
                publisher.close()
                running = false
              }
            }
        }

        fun replayMessage(message: AuthenticatedMessageAndKey) {
            startStopLock.read {
                if (running) {
                    val records = processAuthenticatedMessage(message)
                    publisher.publish(records)
                } else {
                    throw MessageAddedForReplayWhenNotStartedException(this::class.java.simpleName)
                }
            }
        }
    }

    private class MessageTracker(private val replayScheduler: ReplayScheduler<AuthenticatedMessageAndKey>)  {

        companion object {
            private val logger = LoggerFactory.getLogger(this::class.java.name)
        }

        val processor = object : StateAndEventProcessor<String, AuthenticatedMessageDeliveryState, AppMessageMarker> {
            override fun onNext(
                state: AuthenticatedMessageDeliveryState?,
                event: Record<String, AppMessageMarker>
            ): Response<AuthenticatedMessageDeliveryState> {
                val marker = event.value
                if (marker == null) {
                    logger.error("Received a null event. The state was not updated.")
                    return respond(state)
                }
                val markerType = marker.marker
                val timestamp = marker.timestamp
                return when (markerType) {
                    is LinkManagerSentMarker -> Response(AuthenticatedMessageDeliveryState(markerType.message, timestamp), emptyList())
                    is LinkManagerReceivedMarker -> Response(null, emptyList())
                    else -> respond(state)
                }
            }

            private fun respond(state: AuthenticatedMessageDeliveryState?): Response<AuthenticatedMessageDeliveryState> {
                return Response(state, emptyList())
            }

            override val keyClass = String::class.java
            override val stateValueClass =  AuthenticatedMessageDeliveryState::class.java
            override val eventValueClass = AppMessageMarker::class.java

        }

        val listener = object : StateAndEventListener<String, AuthenticatedMessageDeliveryState> {
            override fun onPostCommit(updatedStates: Map<String, AuthenticatedMessageDeliveryState?>) {
                for ((key, state) in updatedStates) {
                    if (state != null) {
                        replayScheduler.addForReplay(state.timestamp, key, state.message)
                    } else {
                        replayScheduler.removeFromReplay(key)
                    }
                }
            }

            override fun onPartitionLost(states: Map<String, AuthenticatedMessageDeliveryState>) {
                for ((key, _) in states) {
                    replayScheduler.removeFromReplay(key)
                }
            }

            override fun onPartitionSynced(states: Map<String, AuthenticatedMessageDeliveryState>) {
                for ((key, state) in states) {
                    replayScheduler.addForReplay(state.timestamp, key, state.message)
                }
            }
        }
    }
}