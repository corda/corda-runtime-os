package net.corda.p2p.linkmanager.delivery

import com.typesafe.config.Config
import net.corda.configuration.read.ConfigurationReadService
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.ConfigurationChangeHandler
import net.corda.lifecycle.domino.logic.DominoTile
import net.corda.lifecycle.domino.logic.util.ResourcesHolder
import net.corda.p2p.linkmanager.AutoClosableScheduledExecutorService
import net.corda.v5.base.util.contextLogger
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * This class keeps track of messages which may need to be replayed.
 */
class ReplayScheduler<M>(
    coordinatorFactory: LifecycleCoordinatorFactory,
    private val configReadService: ConfigurationReadService,
    private val replayPeriodKey: String,
    private val replayMessage: (message: M) -> Unit,
    children: Set<DominoTile>,
    private val currentTimestamp: () -> Long = { Instant.now().toEpochMilli() },
    ) : Lifecycle {

    val dominoTile = DominoTile(this::class.java.simpleName, coordinatorFactory, ::createResources, children, ReplaySchedulerConfigurationChangeHandler())

    private val replayPeriod = AtomicReference<Duration>()

    @Volatile
    private lateinit var executorService: ScheduledExecutorService
    private val replayFutures = ConcurrentHashMap<String, ScheduledFuture<*>>()

    companion object {
        private val logger = contextLogger()
    }

    override val isRunning: Boolean
        get() = dominoTile.isRunning

    override fun start() {
        if (!isRunning) {
            dominoTile.start()
            executorService = Executors.newSingleThreadScheduledExecutor()
        }
    }

    override fun stop() {
        if (isRunning) {
            dominoTile.stop()
            executorService.shutdown()
        }
    }

    private inner class ReplaySchedulerConfigurationChangeHandler: ConfigurationChangeHandler<Duration>(configReadService,
        LinkManagerConfiguration.CONFIG_KEY,
        ::fromConfig) {
        override fun applyNewConfiguration(newConfiguration: Duration, oldConfiguration: Duration?, resources: ResourcesHolder) {
            if (newConfiguration != oldConfiguration) {
                replayPeriod.set(newConfiguration)
                dominoTile.configApplied(DominoTile.ConfigUpdateResult.Success)
            } else {
                dominoTile.configApplied(DominoTile.ConfigUpdateResult.NoUpdate)
            }
        }
    }

    private fun fromConfig(config: Config): Duration {
        return Duration.ofMillis(config.getLong(replayPeriodKey))
    }

    private fun createResources(resources: ResourcesHolder) {
        executorService = Executors.newSingleThreadScheduledExecutor()
        resources.keep(AutoClosableScheduledExecutorService(executorService))
        dominoTile.resourcesStarted(false)
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

    @Suppress("TooGenericExceptionCaught")
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