package net.corda.p2p.linkmanager.sessions.expiration

import net.corda.data.p2p.event.SessionDeleted
import net.corda.data.scheduler.ScheduledTaskTrigger
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.statemanager.api.MetadataFilter
import net.corda.libs.statemanager.api.Operation
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.DominoTile
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.lifecycle.domino.logic.util.SubscriptionDominoTile
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.linkmanager.sessions.SessionCache
import net.corda.schema.Schemas.ScheduledTask.SCHEDULED_TASK_NAME_STALE_P2P_SESSION_CLEANUP
import net.corda.schema.Schemas.ScheduledTask.SCHEDULED_TASK_TOPIC_STALE_P2P_SESSION_PROCESSOR
import net.corda.utilities.time.Clock
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.Random

/**
 * Automatically scheduled by Corda for cleaning up staled/orphaned sessions.
 * This task is intended to clean up sessions from the state manager which are not frequently used and/or
 * got evicted from the [SessionCache] for some reason.
 * Eviction can happen on LM restart too, if workers get crashed.
 * We can query sessions which are still in the cache, so we need to make sure the sessions are
 * removed from the cache, not just from the [StateManager] and we publish the required [SessionDeleted] events.
 */
@Suppress("LongParameterList")
internal class StaleSessionProcessor(
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    subscriptionFactory: SubscriptionFactory,
    configuration: SmartConfig,
    private val clock: Clock,
    private val stateManager: StateManager,
    private val sessionCache: SessionCache,
    private val noiseFactory: Random = Random(),
) : DurableProcessor<String, ScheduledTaskTrigger>, LifecycleWithDominoTile {
    private companion object {
        const val STALE_SESSION_PROCESSOR_GROUP = "stale_session_processor_group"
        val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val subscriptionConfig = SubscriptionConfig(
        STALE_SESSION_PROCESSOR_GROUP,
        SCHEDULED_TASK_TOPIC_STALE_P2P_SESSION_PROCESSOR,
    )

    private val subscription = {
        subscriptionFactory.createDurableSubscription(
            subscriptionConfig,
            this,
            configuration,
            null,
        )
    }

    private val subscriptionTile = SubscriptionDominoTile(
        lifecycleCoordinatorFactory,
        subscription,
        subscriptionConfig,
        setOf(stateManager.name),
        emptySet(),
    )

    override val dominoTile: DominoTile = ComplexDominoTile(
        this::class.java.simpleName,
        lifecycleCoordinatorFactory,
        dependentChildren = setOf(subscriptionTile.coordinatorName),
        managedChildren = setOf(subscriptionTile.toNamedLifecycle()),
    )

    override fun onNext(events: List<Record<String, ScheduledTaskTrigger>>): List<Record<*, *>> {
        events.filter { it.key == SCHEDULED_TASK_NAME_STALE_P2P_SESSION_CLEANUP }.forEach { _ ->
            logger.info("Scheduled task is triggered to clean up stale sessions.")
            var expiredStates = listOf<State>()
            try {
                val noise = Duration.of(
                    noiseFactory.nextLong(20 * 60),
                    TimeUnit.MILLISECONDS.toChronoUnit(),
                )
                val now = clock.instant()
                logger.info("noise is: $noise")
                logger.info("time was: ${(now - noise)}")
                expiredStates = stateManager.findByMetadataMatchingAny(
                    listOf(MetadataFilter("expiry", Operation.LesserThan, (now - noise).toString()))
                ).values.toList()
            } catch (e: Exception) {
                logger.error("Unexpected error while trying to execute the scheduled delete task " +
                        "for expired sessions from the state manager.", e)
            }

            if (expiredStates.isNotEmpty()) {
                expiredStates.forEach { state ->
                    sessionCache.forgetState(state)
                }
            }
            logger.info("Scheduled task is finished. Stale sessions are cleaned up.")
        }

        // we don't need to publish anything after finishing the scheduled task
        return emptyList()
    }

    override val keyClass = String::class.java
    override val valueClass = ScheduledTaskTrigger::class.java
}