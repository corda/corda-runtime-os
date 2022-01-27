package net.corda.p2p.linkmanager.delivery

import net.corda.configuration.read.ConfigurationReadService
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.MESSAGE_REPLAY_KEY_PREFIX
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.DominoTile
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.lifecycle.domino.logic.util.PublisherWithDominoLogic
import net.corda.lifecycle.domino.logic.util.ResourcesHolder
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.processor.StateAndEventProcessor.Response
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.listener.StateAndEventListener
import net.corda.p2p.AuthenticatedMessageAndKey
import net.corda.p2p.AuthenticatedMessageDeliveryState
import net.corda.p2p.linkmanager.LinkManagerCryptoService
import net.corda.p2p.linkmanager.LinkManagerNetworkMap
import net.corda.p2p.linkmanager.LinkManagerNetworkMap.Companion.toHoldingIdentity
import net.corda.p2p.linkmanager.sessions.SessionManager
import net.corda.p2p.markers.AppMessageMarker
import net.corda.p2p.markers.LinkManagerReceivedMarker
import net.corda.p2p.markers.LinkManagerSentMarker
import net.corda.schema.Schemas.P2P.Companion.P2P_OUT_MARKERS
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

@Suppress("LongParameterList")
class DeliveryTracker(
    coordinatorFactory: LifecycleCoordinatorFactory,
    configReadService: ConfigurationReadService,
    publisherFactory: PublisherFactory,
    private val configuration: SmartConfig,
    private val subscriptionFactory: SubscriptionFactory,
    networkMap: LinkManagerNetworkMap,
    cryptoService: LinkManagerCryptoService,
    sessionManager: SessionManager,
    private val instanceId: Int,
    processAuthenticatedMessage: (message: AuthenticatedMessageAndKey) -> List<Record<String, *>>,
    ): LifecycleWithDominoTile {

    private val appMessageReplayer = AppMessageReplayer(
        coordinatorFactory,
        publisherFactory,
        configuration,
        processAuthenticatedMessage
    )
    private val replayScheduler = ReplayScheduler(
        coordinatorFactory,
        configReadService,
        ExponentialBackoffReplayCalculator(),
        MESSAGE_REPLAY_KEY_PREFIX,
        appMessageReplayer::replayMessage,
    )

    override val dominoTile = DominoTile(this::class.java.simpleName, coordinatorFactory, ::createResources,
        setOf(
            replayScheduler.dominoTile,
            networkMap.dominoTile,
            cryptoService.dominoTile,
            sessionManager.dominoTile,
            appMessageReplayer.dominoTile
        )
    )

    private val messageTracker = MessageTracker(replayScheduler)
    private val messageTrackerSubscription = subscriptionFactory.createStateAndEventSubscription(
        SubscriptionConfig("message-tracker-group", P2P_OUT_MARKERS, instanceId),
        messageTracker.processor,
        configuration,
        messageTracker.listener
    )

    private fun createResources(resources: ResourcesHolder): CompletableFuture<Unit> {
        messageTrackerSubscription.start()
        resources.keep { messageTrackerSubscription.stop() }
        val future = CompletableFuture<Unit>()
        future.complete(Unit)
        return future
    }

    private class AppMessageReplayer(
        coordinatorFactory: LifecycleCoordinatorFactory,
        publisherFactory: PublisherFactory,
        configuration: SmartConfig,
        private val processAuthenticatedMessage: (message: AuthenticatedMessageAndKey) -> List<Record<String, *>>
    ): LifecycleWithDominoTile {

        companion object {
            const val MESSAGE_REPLAYER_CLIENT_ID = "message-replayer-client"
            private val logger = contextLogger()
        }

        private val publisher = PublisherWithDominoLogic(
            publisherFactory,
            coordinatorFactory,
            PublisherConfig(MESSAGE_REPLAYER_CLIENT_ID),
            configuration
        )

        override val dominoTile = publisher.dominoTile

        fun replayMessage(message: AuthenticatedMessageAndKey) {
            dominoTile.withLifecycleLock {
                if (!isRunning) {
                    throw IllegalStateException("A message was added for replay before the DeliveryTracker was started.")
                }

                val records = processAuthenticatedMessage(message)
                logger.debug { "Replaying data message ${message.message.header.messageId}." }
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

            private val trackedSessionKeys = ConcurrentHashMap<String, SessionManager.SessionKey>()

            override fun onPostCommit(updatedStates: Map<String, AuthenticatedMessageDeliveryState?>) {
                for ((key, state) in updatedStates) {
                    if (state != null) {
                        val sessionKey = sessionKeyFromState(state)
                        trackedSessionKeys[key] = sessionKey
                        replayScheduler.addForReplay(state.timestamp, key, state.message, sessionKey)
                    } else {
                        val sessionKey = trackedSessionKeys.remove(key)
                        sessionKey?.let { replayScheduler.removeFromReplay(key, sessionKey) }
                    }
                }
            }

            override fun onPartitionLost(states: Map<String, AuthenticatedMessageDeliveryState>) {
                for ((key, state) in states) {
                    replayScheduler.removeFromReplay(key, sessionKeyFromState(state))
                    trackedSessionKeys.remove(key)
                }
            }

            override fun onPartitionSynced(states: Map<String, AuthenticatedMessageDeliveryState>) {
                for ((key, state) in states) {
                    val sessionKey = sessionKeyFromState(state)
                    trackedSessionKeys[key] = sessionKey
                    replayScheduler.addForReplay(state.timestamp, key, state.message, sessionKey)
                }
            }

            private fun sessionKeyFromState(state: AuthenticatedMessageDeliveryState): SessionManager.SessionKey {
                val header = state.message.message.header
                return SessionManager.SessionKey(
                    header.source.toHoldingIdentity(),
                    header.destination.toHoldingIdentity()
                )
            }
        }
    }
}