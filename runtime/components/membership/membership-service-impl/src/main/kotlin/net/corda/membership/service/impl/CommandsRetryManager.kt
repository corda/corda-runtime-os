package net.corda.membership.service.impl

import net.corda.data.membership.async.request.MembershipAsyncRequest
import net.corda.data.membership.async.request.MembershipAsyncRequestState
import net.corda.data.membership.async.request.RegistrationAsyncRequest
import net.corda.data.membership.async.request.RetriableFailure
import net.corda.data.membership.async.request.SentToMgmWaitingForNetwork
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.Resource
import net.corda.messaging.api.processor.StateAndEventProcessor
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
) : Resource,
    StateAndEventListener<String, MembershipAsyncRequestState>,
    StateAndEventProcessor<String, MembershipAsyncRequestState, MembershipAsyncRequestState> {
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

    private val timers = ConcurrentHashMap<String, ScheduledFuture<*>>()

    override fun onNext(
        state: MembershipAsyncRequestState?,
        event: Record<String, MembershipAsyncRequestState>,
    ): StateAndEventProcessor.Response<MembershipAsyncRequestState> {
        return StateAndEventProcessor.Response(
            updatedState = event.value,
            responseEvents = emptyList(),
            markForDLQ = false,
        )
    }

    override val keyClass = String::class.java
    override val stateValueClass = MembershipAsyncRequestState::class.java
    override val eventValueClass = MembershipAsyncRequestState::class.java

    override fun close() {
        scheduledExecutorService.shutdownNow()
        scheduledExecutorService.awaitTermination(1, TimeUnit.MINUTES)
        publisher.close()
    }

    override fun onPartitionSynced(states: Map<String, MembershipAsyncRequestState>) {
        states.forEach(::addTimer)
    }

    override fun onPartitionLost(states: Map<String, MembershipAsyncRequestState>) {
        states.keys.forEach(::cancelTimers)
    }

    override fun onPostCommit(updatedStates: Map<String, MembershipAsyncRequestState?>) {
        updatedStates.forEach { (requestId, state) ->
            if (state == null) {
                cancelTimers(requestId)
            } else {
                addTimer(requestId, state)
            }
        }
    }

    private fun addTimer(requestId: String, state: MembershipAsyncRequestState) {
        val duration = when (val cause = state.cause) {
            is SentToMgmWaitingForNetwork -> Duration.ofSeconds(WAIT_AFTER_SENT_TO_MGM_SECONDS)
            is RetriableFailure -> Duration.between(clock.instant(), cause.nextTryAt)
            else -> Duration.ofSeconds(WAIT_AFTER_SENT_TO_MGM_SECONDS)
        }
        if (duration.isNegative) {
            publishEvent(state.request, state)
        } else {
            logger.debug("Request $requestId will be retried in ${duration.seconds} seconds")
            timers.compute(requestId) { _, future ->
                future?.cancel(false)
                scheduledExecutorService.schedule(
                    {
                        publishEvent(state.request, state)
                    },
                    duration.toMillis(),
                    TimeUnit.MILLISECONDS,
                )
            }
        }
    }

    private fun cancelTimers(requestId: String) {
        val future = timers.remove(requestId)
        if (future != null) {
            logger.debug("Request $requestId will not be retried")
            future.cancel(false)
        }
    }

    private fun publishEvent(
        request: RegistrationAsyncRequest,
        state: MembershipAsyncRequestState,
    ) {
        val requestId = request.requestId
        val holdingId = request.holdingIdentityId
        logger.info("Retrying request $requestId")
        val event = Record(
            MEMBERSHIP_ASYNC_REQUEST_TOPIC,
            holdingId,
            MembershipAsyncRequest(
                request,
                state,
            ),
        )
        publisher.publish(
            listOf(
                event,
            ),
        )
        timers.remove(requestId)
    }
}
