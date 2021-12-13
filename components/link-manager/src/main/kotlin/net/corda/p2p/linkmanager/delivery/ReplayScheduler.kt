package net.corda.p2p.linkmanager.delivery

import com.typesafe.config.Config
import net.corda.configuration.read.ConfigurationReadService
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.ConfigurationChangeHandler
import net.corda.lifecycle.domino.logic.DominoTile
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.lifecycle.domino.logic.util.ResourcesHolder
import net.corda.p2p.linkmanager.utilities.AutoClosableScheduledExecutorService
import net.corda.v5.base.util.contextLogger
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * This class keeps track of messages which may need to be replayed.
 */
@Suppress("LongParameterList")
class ReplayScheduler<M>(
    coordinatorFactory: LifecycleCoordinatorFactory,
    private val configReadService: ConfigurationReadService,
    private val replayPeriodKey: String,
    private val replayMessage: (message: M) -> Unit,
    private val currentTimestamp: () -> Long = { Instant.now().toEpochMilli() },
    ) : LifecycleWithDominoTile {

    override val dominoTile = DominoTile(
        this::class.java.simpleName,
        coordinatorFactory,
        ::createResources,
        configurationChangeHandler = ReplaySchedulerConfigurationChangeHandler()
    )

    private val replayPeriod = AtomicReference<Duration>()

    @Volatile
    private lateinit var executorService: ScheduledExecutorService
    private val replayFutures = ConcurrentHashMap<String, ScheduledFuture<*>>()

    companion object {
        private val logger = contextLogger()
    }

    inner class ReplaySchedulerConfigurationChangeHandler: ConfigurationChangeHandler<Duration>(configReadService,
        LinkManagerConfiguration.CONFIG_KEY,
        ::fromConfig) {
        override fun applyNewConfiguration(
            newConfiguration: Duration,
            oldConfiguration: Duration?,
            resources: ResourcesHolder,
        ): CompletableFuture<Unit> {
            val configUpdateResult = CompletableFuture<Unit>()
            if (newConfiguration.isNegative) {
                configUpdateResult.completeExceptionally(
                    IllegalArgumentException("The duration configuration (with key $replayPeriod) must be positive.")
                )
                return configUpdateResult
            }
            replayPeriod.set(newConfiguration)
            configUpdateResult.complete(Unit)
            return configUpdateResult
        }
    }

    private fun fromConfig(config: Config): Duration {
        return config.getDuration(replayPeriodKey)
    }

    private fun createResources(resources: ResourcesHolder): CompletableFuture<Unit> {
        executorService = Executors.newSingleThreadScheduledExecutor()
        resources.keep(AutoClosableScheduledExecutorService(executorService))
        val future = CompletableFuture<Unit>()
        future.complete(Unit)
        return future
    }

    fun addForReplay(originalAttemptTimestamp: Long, uniqueId: String, message: M) {
         dominoTile.withLifecycleLock {
            if (!isRunning) {
                throw IllegalStateException("A message was added for replay before the ReplayScheduler was started.")
            }
            val delay = replayPeriod.get().toMillis() + originalAttemptTimestamp - currentTimestamp()
            val future = executorService.schedule({ replay(message, uniqueId) }, delay, TimeUnit.MILLISECONDS)
            replayFutures[uniqueId] = future
        }
    }

    fun removeFromReplay(uniqueId: String) {
        val removedFuture = replayFutures.remove(uniqueId)
        removedFuture?.cancel(false)
    }

    fun removeAllMessagesFromReplay() {
        replayFutures.keys.forEach { removeFromReplay(it) }
    }

    private fun replay(message: M, uniqueId: String) {
        try {
            replayMessage(message)
        } catch (exception: Exception) {
            logger.error("An exception was thrown when replaying a message. The task will be retried again in " +
                "${replayPeriod.get().toMillis()} ms.\nException:",
                exception
            )
        }
        reschedule(message, uniqueId)
    }

    private fun reschedule(message: M, uniqueId: String) {
        replayFutures.computeIfPresent(uniqueId) { _, _ ->
            executorService.schedule({ replay(message, uniqueId) }, replayPeriod.get().toMillis(), TimeUnit.MILLISECONDS)
        }
    }
}