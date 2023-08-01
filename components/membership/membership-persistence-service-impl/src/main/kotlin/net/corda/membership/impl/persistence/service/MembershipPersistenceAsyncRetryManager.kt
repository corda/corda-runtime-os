package net.corda.membership.impl.persistence.service

import net.corda.data.membership.db.request.async.MembershipPersistenceAsyncRequest
import net.corda.data.membership.db.request.async.MembershipPersistenceAsyncRequestState
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.TimerEvent
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.listener.StateAndEventListener
import net.corda.schema.Schemas.Membership.MEMBERSHIP_DB_ASYNC_TOPIC
import net.corda.utilities.time.Clock
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.Long.max
import java.util.concurrent.TimeUnit

internal class MembershipPersistenceAsyncRetryManager(
    coordinatorFactory: LifecycleCoordinatorFactory,
    private val publisherFactory: PublisherFactory,
    private val clock: Clock,
) :
    StateAndEventListener<String, MembershipPersistenceAsyncRequestState> {

    private companion object {
        const val PUBLISHER_NAME = "MembershipPersistenceAsyncRetryManager"
        const val WAIT_BETWEEN_REQUESTS_IN_SECONDS = 2L
        val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val coordinator =
        coordinatorFactory.createCoordinator<MembershipPersistenceAsyncRetryManager> { event, _ ->
            eventHandler(event)
        }

    private data class RetryEvent(
        val event: Record<String, MembershipPersistenceAsyncRequest>,
        override val key: String,
    ) : TimerEvent

    private fun eventHandler(
        event: LifecycleEvent,
    ) {
        if (event is RetryEvent) {
            logger.info("Retrying request ${event.key}")
            coordinator.getManagedResource<Publisher>(PUBLISHER_NAME)?.publish(
                listOf(
                    event.event,
                )
            )
        }
    }

    fun start(messagingConfig: SmartConfig) {
        coordinator.start()
        coordinator.createManagedResource(PUBLISHER_NAME) {
            publisherFactory.createPublisher(
                publisherConfig = PublisherConfig(PUBLISHER_NAME),
                messagingConfig = messagingConfig,
            ).also {
                it.start()
            }
        }
        coordinator.updateStatus(LifecycleStatus.UP)
    }

    fun stop() {
        coordinator.closeManagedResources(setOf(PUBLISHER_NAME))
        coordinator.stop()
    }

    override fun onPartitionSynced(states: Map<String, MembershipPersistenceAsyncRequestState>) {
        states.forEach(::addTimer)
    }

    override fun onPartitionLost(states: Map<String, MembershipPersistenceAsyncRequestState>) {
        states.keys.forEach(::cancelTimer)
    }

    override fun onPostCommit(updatedStates: Map<String, MembershipPersistenceAsyncRequestState?>) {
        updatedStates.forEach { (key, state) ->
            if (state == null) {
                cancelTimer(key)
            } else {
                addTimer(key, state)
            }
        }
    }

    private fun addTimer(key: String, state: MembershipPersistenceAsyncRequestState) {
        val durationInMillis = max(
            0,
            (state.lastFailedOn.toEpochMilli() + TimeUnit.SECONDS.toMillis(WAIT_BETWEEN_REQUESTS_IN_SECONDS)) -
                (clock.instant().toEpochMilli())
        )
        val event = Record(
            MEMBERSHIP_DB_ASYNC_TOPIC,
            key,
            state.request,
        )
        logger.info("Request $key will be retried in $durationInMillis milliseconds")
        coordinator.setTimer("retry-$key", durationInMillis) {
            RetryEvent(event, it)
        }
    }

    private fun cancelTimer(key: String) {
        logger.info("Request $key will not be retried")
        coordinator.cancelTimer("retry-$key")
    }
}
