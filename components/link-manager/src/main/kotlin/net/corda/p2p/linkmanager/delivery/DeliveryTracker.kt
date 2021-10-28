package net.corda.p2p.linkmanager.delivery

import net.corda.configuration.read.ConfigurationReadService
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.MESSAGE_REPLAY_PERIOD_KEY
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.DominoTile
import net.corda.lifecycle.domino.logic.util.PublisherWithDominoLogic
import net.corda.lifecycle.domino.logic.util.ResourcesHolder
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.processor.StateAndEventProcessor.Response
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

class DeliveryTracker(
    coordinatorFactory: LifecycleCoordinatorFactory,
    configReadService: ConfigurationReadService,
    publisherFactory: PublisherFactory,
    private val subscriptionFactory: SubscriptionFactory,
    childrenUsedByProcessAuthenticatedMessage: Set<DominoTile>,
    processAuthenticatedMessage: (message: AuthenticatedMessageAndKey) -> List<Record<String, *>>,
    ): Lifecycle {

    private val appMessageReplayer = AppMessageReplayer(coordinatorFactory, publisherFactory, processAuthenticatedMessage)
    private val replayScheduler = ReplayScheduler(
        coordinatorFactory,
        configReadService,
        MESSAGE_REPLAY_PERIOD_KEY,
        appMessageReplayer::replayMessage,
        childrenUsedByProcessAuthenticatedMessage + appMessageReplayer.dominoTile
    )

    val dominoTile = DominoTile(this::class.java.simpleName, coordinatorFactory, ::createResources,
        setOf(replayScheduler.dominoTile))

    override val isRunning: Boolean
        get() = dominoTile.isRunning

    override fun start() {
        if (!isRunning) {
            dominoTile.start()
        }
    }

    override fun stop() {
        if (isRunning) {
            dominoTile.stop()
        }
    }

    private fun createResources(resources: ResourcesHolder) {
        val messageTracker = MessageTracker(replayScheduler)
        val messageTrackerSubscription = subscriptionFactory.createStateAndEventSubscription(
            SubscriptionConfig("message-tracker-group", Schema.P2P_OUT_MARKERS, 1),
            processor = messageTracker.processor,
            stateAndEventListener = messageTracker.listener
        )
        resources.keep(messageTrackerSubscription)
        dominoTile.resourcesStarted(false)
    }

    private class AppMessageReplayer(
        coordinatorFactory: LifecycleCoordinatorFactory,
        publisherFactory: PublisherFactory,
        private val processAuthenticatedMessage: (message: AuthenticatedMessageAndKey) -> List<Record<String, *>>
    ): Lifecycle {

        companion object {
            const val MESSAGE_REPLAYER_CLIENT_ID = "message-replayer-client"
        }

        private val publisher = PublisherWithDominoLogic(publisherFactory, coordinatorFactory, MESSAGE_REPLAYER_CLIENT_ID)

        val dominoTile = publisher.dominoTile

        override val isRunning: Boolean
            get() = dominoTile.isRunning

        override fun start() {
            if (!isRunning) {
                dominoTile.start()
            }
        }

        override fun stop() {
            if (isRunning) {
                dominoTile.stop()
            }
        }

        fun replayMessage(message: AuthenticatedMessageAndKey) {
            dominoTile.withLifecycleLock {
                if (!isRunning) {
                    throw IllegalStateException("A message was added for replay before the DeliveryTracker was started.")
                }

                val records = processAuthenticatedMessage(message)
                publisher.publish(records)
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