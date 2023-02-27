package net.corda.p2p.linkmanager.delivery

import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.client.CryptoOpsClient
import net.corda.data.p2p.AuthenticatedMessageAndKey
import net.corda.data.p2p.AuthenticatedMessageDeliveryState
import net.corda.data.p2p.app.AuthenticatedMessage
import net.corda.data.p2p.markers.AppMessageMarker
import net.corda.data.p2p.markers.LinkManagerProcessedMarker
import net.corda.data.p2p.markers.LinkManagerReceivedMarker
import net.corda.data.p2p.markers.TtlExpiredMarker
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.lifecycle.domino.logic.util.PublisherWithDominoLogic
import net.corda.lifecycle.domino.logic.util.StateAndEventSubscriptionDominoTile
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.processor.StateAndEventProcessor.Response
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.listener.StateAndEventListener
import net.corda.metrics.CordaMetrics
import net.corda.p2p.linkmanager.sessions.SessionManager
import net.corda.schema.Schemas.P2P.Companion.P2P_OUT_MARKERS
import net.corda.utilities.time.Clock
import net.corda.v5.base.util.debug
import net.corda.virtualnode.toCorda
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Suppress("LongParameterList")
internal class DeliveryTracker(
    coordinatorFactory: LifecycleCoordinatorFactory,
    configReadService: ConfigurationReadService,
    publisherFactory: PublisherFactory,
    messagingConfiguration: SmartConfig,
    subscriptionFactory: SubscriptionFactory,
    sessionManager: SessionManager,
    clock: Clock,
    processAuthenticatedMessage: (message: AuthenticatedMessageAndKey) -> List<Record<String, *>>,
    ): LifecycleWithDominoTile {

    private val appMessageReplayer = AppMessageReplayer(
        coordinatorFactory,
        publisherFactory,
        messagingConfiguration,
        processAuthenticatedMessage
    )
    private val replayScheduler = ReplayScheduler<SessionManager.Counterparties, AuthenticatedMessageAndKey>(
        coordinatorFactory,
        configReadService,
        true,
        appMessageReplayer::replayMessage,
        clock = clock
    )

    private val messageTracker = MessageTracker(replayScheduler)
    private val subscriptionConfig = SubscriptionConfig("message-tracker-group", P2P_OUT_MARKERS)
    private val messageTrackerSubscription = {
        subscriptionFactory.createStateAndEventSubscription(
            subscriptionConfig,
            messageTracker.processor,
            messagingConfiguration,
            messageTracker.listener
        )
    }
    private val messageTrackerSubscriptionTile = StateAndEventSubscriptionDominoTile(
        coordinatorFactory,
        messageTrackerSubscription,
        subscriptionConfig,
        setOf(
            replayScheduler.dominoTile.coordinatorName,
            LifecycleCoordinatorName.forComponent<GroupPolicyProvider>(),
            LifecycleCoordinatorName.forComponent<MembershipGroupReaderProvider>(),
            LifecycleCoordinatorName.forComponent<CryptoOpsClient>(),
            sessionManager.dominoTile.coordinatorName,
            appMessageReplayer.dominoTile.coordinatorName
        ),
        setOf(
            replayScheduler.dominoTile.toNamedLifecycle(),
            appMessageReplayer.dominoTile.toNamedLifecycle()
        )
    )

    override val dominoTile = ComplexDominoTile(this::class.java.simpleName, coordinatorFactory,
        dependentChildren = setOf(messageTrackerSubscriptionTile.coordinatorName),
        managedChildren = setOf(messageTrackerSubscriptionTile.toNamedLifecycle())
    )

    private class AppMessageReplayer(
        coordinatorFactory: LifecycleCoordinatorFactory,
        publisherFactory: PublisherFactory,
        messagingConfiguration: SmartConfig,
        private val processAuthenticatedMessage: (message: AuthenticatedMessageAndKey) -> List<Record<String, *>>
    ): LifecycleWithDominoTile {

        companion object {
            const val MESSAGE_REPLAYER_CLIENT_ID = "message-replayer-client"
            private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        }

        private val publisher = PublisherWithDominoLogic(
            publisherFactory,
            coordinatorFactory,
                PublisherConfig(MESSAGE_REPLAYER_CLIENT_ID, true),
            messagingConfiguration
        )

        override val dominoTile = publisher.dominoTile

        fun replayMessage(message: AuthenticatedMessageAndKey) {
            publisher.withLifecycleLock {
                if (!isRunning) {
                    throw IllegalStateException("A message was added for replay before the DeliveryTracker was started.")
                }

                val records = processAuthenticatedMessage(message)
                logger.debug { "Replaying data message ${message.message.header.messageId}." }
                publisher.publish(records)
                recordReplaysMetric(message.message)
            }
        }

        private fun recordReplaysMetric(message: AuthenticatedMessage) {
            CordaMetrics.Metric.OutboundMessageReplayCount.builder()
                .withTag(CordaMetrics.Tag.SourceVirtualNode, message.header.source.x500Name)
                .withTag(CordaMetrics.Tag.DestinationVirtualNode, message.header.destination.x500Name)
                .withTag(CordaMetrics.Tag.MembershipGroup, message.header.source.groupId)
                .build().increment()
        }
    }

    private class MessageTracker(
        private val replayScheduler: ReplayScheduler<SessionManager.Counterparties, AuthenticatedMessageAndKey>
    )  {

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
                    is LinkManagerProcessedMarker -> Response(AuthenticatedMessageDeliveryState(markerType.message, timestamp), emptyList())
                    is LinkManagerReceivedMarker -> {
                        if (state != null) {
                            // if we receive multiple acknowledgements, it is possible the state might have been nullified already.
                            // Only the first one matters for calculating the end-to-end delivery latency anyway.
                            recordDeliveryLatencyMetric(state)
                        }
                        Response(null, emptyList())
                    }
                    is TtlExpiredMarker -> {
                        if (state != null) {
                            recordTtlExpiredMetric(state)
                        }
                        Response(null, emptyList())
                    }
                    else -> respond(state)
                }
            }

            private fun respond(state: AuthenticatedMessageDeliveryState?): Response<AuthenticatedMessageDeliveryState> {
                return Response(state, emptyList())
            }

            private fun recordDeliveryLatencyMetric(state: AuthenticatedMessageDeliveryState) {
                val originalProcessingTime = Instant.ofEpochMilli(state.timestamp)
                val deliveryLatency = Duration.between(originalProcessingTime, Instant.now())
                val header = state.message.message.header
                CordaMetrics.Metric.OutboundMessageDeliveryLatency.builder()
                    .withTag(CordaMetrics.Tag.SourceVirtualNode, header.source.x500Name)
                    .withTag(CordaMetrics.Tag.DestinationVirtualNode, header.destination.x500Name)
                    .withTag(CordaMetrics.Tag.MembershipGroup, header.source.groupId)
                    .withTag(CordaMetrics.Tag.MessagingSubsystem, header.subsystem)
                    .build().record(deliveryLatency)
            }

            private fun recordTtlExpiredMetric(state: AuthenticatedMessageDeliveryState) {
                val header = state.message.message.header
                CordaMetrics.Metric.OutboundMessageTtlExpired.builder()
                    .withTag(CordaMetrics.Tag.SourceVirtualNode, header.source.x500Name)
                    .withTag(CordaMetrics.Tag.DestinationVirtualNode, header.destination.x500Name)
                    .withTag(CordaMetrics.Tag.MembershipGroup, header.source.groupId)
                    .withTag(CordaMetrics.Tag.MessagingSubsystem, header.subsystem)
                    .build().increment()
            }

            override val keyClass = String::class.java
            override val stateValueClass =  AuthenticatedMessageDeliveryState::class.java
            override val eventValueClass = AppMessageMarker::class.java

        }

        val listener = object : StateAndEventListener<String, AuthenticatedMessageDeliveryState> {

            private val trackedCounterparties = ConcurrentHashMap<String, SessionManager.Counterparties>()

            override fun onPostCommit(updatedStates: Map<String, AuthenticatedMessageDeliveryState?>) {
                for ((key, state) in updatedStates) {
                    if (state != null) {
                        val counterparties = counterpartiesFromState(state)
                        trackedCounterparties[key] = counterparties
                        replayScheduler.addForReplay(state.timestamp, key, state.message, counterparties)
                    } else {
                        val counterparties = trackedCounterparties.remove(key)
                        counterparties?.let { replayScheduler.removeFromReplay(key, counterparties) }
                    }
                }
            }

            override fun onPartitionLost(states: Map<String, AuthenticatedMessageDeliveryState>) {
                for ((key, state) in states) {
                    replayScheduler.removeFromReplay(key, counterpartiesFromState(state))
                    trackedCounterparties.remove(key)
                }
            }

            override fun onPartitionSynced(states: Map<String, AuthenticatedMessageDeliveryState>) {
                for ((key, state) in states) {
                    val sessionCounterparties = counterpartiesFromState(state)
                    trackedCounterparties[key] = sessionCounterparties
                    replayScheduler.addForReplay(state.timestamp, key, state.message, sessionCounterparties)
                }
            }

            private fun counterpartiesFromState(state: AuthenticatedMessageDeliveryState): SessionManager.Counterparties {
                val header = state.message.message.header
                return SessionManager.Counterparties(
                    header.source.toCorda(),
                    header.destination.toCorda()
                )
            }
        }
    }
}
