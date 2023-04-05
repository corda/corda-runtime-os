package net.corda.membership.service.impl

import net.corda.data.membership.async.request.MembershipAsyncRequest
import net.corda.data.membership.async.request.MembershipAsyncRequestState
import net.corda.data.membership.async.request.RetriableFailure
import net.corda.data.membership.async.request.SentToMgmWaitingForNetwork
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.Resource
import net.corda.lifecycle.TimerEvent
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.listener.StateAndEventListener
import net.corda.schema.Schemas.Membership.MEMBERSHIP_ASYNC_REQUEST_TOPIC
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

internal class CommandsRetryManager(
    coordinatorFactory: LifecycleCoordinatorFactory,
    publisherFactory: PublisherFactory,
    messagingConfig: SmartConfig,
) : Resource, StateAndEventListener<String, MembershipAsyncRequestState> {
    private companion object {
        const val PUBLISHER_NAME = "MembershipServiceAsyncCommandsRetryManager"
        val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        const val WAIT_AFTER_FAILURE_IN_SECONDS = 10L
        const val WAIT_AFTER_SENT_TO_MGM_SECONDS = 40L
    }
    private val publisher = publisherFactory.createPublisher(
        publisherConfig = PublisherConfig(PUBLISHER_NAME),
        messagingConfig = messagingConfig,
    ).also {
        it.start()
    }
    private val coordinator =
        coordinatorFactory.createCoordinator<CommandsRetryManager> { event, _ ->
            eventHandler(event)
        }.also {
            it.start()
            it.updateStatus(LifecycleStatus.UP)
        }

    private data class RetryEvent(
        val event: Record<String, MembershipAsyncRequest>,
        override val key: String,
    ) : TimerEvent

    private fun eventHandler(
        event: LifecycleEvent,
    ) {
        if (event is RetryEvent) {
            logger.info("Retrying request ${event.key}")
            publisher.publish(
                listOf(
                    event.event,
                ),
            )
        }
    }

    override fun close() {
        publisher.close()
        coordinator.close()
    }

    override fun onPartitionSynced(states: Map<String, MembershipAsyncRequestState>) {
        states.forEach(::addTimer)
    }

    override fun onPartitionLost(states: Map<String, MembershipAsyncRequestState>) {
        states.keys.forEach(::cancelTimer)
    }

    override fun onPostCommit(updatedStates: Map<String, MembershipAsyncRequestState?>) {
        updatedStates.forEach { (key, state) ->
            if (state == null) {
                cancelTimer(key)
            } else {
                addTimer(key, state)
            }
        }
    }

    private fun addTimer(key: String, state: MembershipAsyncRequestState) {
        val durationInSeconds = when (state.cause) {
            is SentToMgmWaitingForNetwork -> WAIT_AFTER_SENT_TO_MGM_SECONDS
            is RetriableFailure -> WAIT_AFTER_FAILURE_IN_SECONDS
            else -> WAIT_AFTER_FAILURE_IN_SECONDS
        }
        val event = Record(
            MEMBERSHIP_ASYNC_REQUEST_TOPIC,
            key,
            state.request,
        )
        logger.debug("Request $key will be retried in $durationInSeconds seconds")
        coordinator.setTimer("retry-$key", TimeUnit.SECONDS.toMillis(durationInSeconds)) {
            RetryEvent(event, it)
        }
    }

    private fun cancelTimer(key: String) {
        logger.debug("Request $key will not be retried")
        coordinator.cancelTimer("retry-$key")
    }
}
