package net.corda.membership.service.impl

import net.corda.data.membership.async.request.MembershipAsyncRequest
import net.corda.data.membership.async.request.MembershipAsyncRequestState
import net.corda.data.membership.async.request.MembershipAsyncRequestStates
import net.corda.data.membership.async.request.RetriableFailure
import net.corda.data.membership.async.request.SentToMgmWaitingForNetwork
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.Resource
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.listener.StateAndEventListener
import net.corda.schema.Schemas.Membership.MEMBERSHIP_ASYNC_REQUEST_TOPIC
import net.corda.utilities.time.UTCClock
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

internal class CommandsRetryManager(
    publisherFactory: PublisherFactory,
    messagingConfig: SmartConfig,
    private val clock: UTCClock,
    private val scheduledExecutorService: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor(),
) : Resource, StateAndEventListener<String, MembershipAsyncRequestStates> {
    private companion object {
        const val PUBLISHER_NAME = "MembershipServiceAsyncCommandsRetryManager"
        val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        const val WAIT_AFTER_SENT_TO_MGM_SECONDS = 40L
    }
    private val publisher = publisherFactory.createPublisher(
        publisherConfig = PublisherConfig(PUBLISHER_NAME),
        messagingConfig = messagingConfig,
    ).also {
        it.start()
    }

    private val timers = ConcurrentHashMap<String, MutableMap<String, ScheduledFuture<*>>>()

    override fun close() {
        scheduledExecutorService.shutdownNow()
        scheduledExecutorService.awaitTermination(1, TimeUnit.MINUTES)
        publisher.close()
    }

    override fun onPartitionSynced(states: Map<String, MembershipAsyncRequestStates>) {
        states.forEach(::addTimers)
    }

    override fun onPartitionLost(states: Map<String, MembershipAsyncRequestStates>) {
        states.keys.forEach(::cancelTimers)
    }

    override fun onPostCommit(updatedStates: Map<String, MembershipAsyncRequestStates?>) {
        updatedStates.forEach { (holdingId, states) ->
            if (states == null) {
                cancelTimers(holdingId)
            } else {
                addTimers(holdingId, states)
            }
        }
    }

    private fun addTimers(holdingId: String, states: MembershipAsyncRequestStates) {
        val requestIdToState = states.states.associateBy {
            it.request.request.requestId
        }
        val timersForMembers = timers[holdingId]
        val keysOfTimersForMembers = timers[holdingId]?.keys ?: emptySet()
        val timersToStop = keysOfTimersForMembers - requestIdToState.keys
        if (timersForMembers != null) {
            timersToStop.forEach { requestId ->
                timersForMembers.remove(requestId)?.cancel(false)
            }
        }
        val timersToStart = requestIdToState - keysOfTimersForMembers
        timersToStart.values.forEach { state ->
            addTimer(holdingId, state)
        }
    }

    private fun addTimer(holdingId: String, state: MembershipAsyncRequestState) {
        val duration = when (val cause = state.cause) {
            is SentToMgmWaitingForNetwork -> Duration.ofSeconds(WAIT_AFTER_SENT_TO_MGM_SECONDS)
            is RetriableFailure -> Duration.between(clock.instant(), cause.nextTryAt)
            else -> Duration.ofSeconds(WAIT_AFTER_SENT_TO_MGM_SECONDS)
        }
        val requestId = state.request.request.requestId
        if (duration.isNegative) {
            publishEvent(holdingId, state.request)
        } else {
            logger.debug("Request $requestId will be retried in ${duration.seconds} seconds")
            timers.computeIfAbsent(holdingId) {
                ConcurrentHashMap()
            }[requestId] = scheduledExecutorService.schedule(
                {
                    publishEvent(holdingId, state.request)
                },
                duration.toMillis(),
                TimeUnit.MILLISECONDS,
            )
        }
    }

    private fun cancelTimers(holdingId: String) {
        timers.remove(holdingId)?.forEach { (requestId, future) ->
            logger.debug("Request $requestId will not be retried")
            future.cancel(false)
        }
    }

    private fun publishEvent(
        holdingId: String,
        request: MembershipAsyncRequest,
    ) {
        val requestId = request.request.requestId
        logger.info("Retrying request $requestId")
        val event = Record(
            MEMBERSHIP_ASYNC_REQUEST_TOPIC,
            holdingId,
            request,
        )
        publisher.publish(
            listOf(
                event,
            ),
        )
        timers.computeIfPresent(holdingId) { _, futures ->
            futures.remove(requestId)
            if (futures.isEmpty()) {
                null
            } else {
                futures
            }
        }
    }
}
