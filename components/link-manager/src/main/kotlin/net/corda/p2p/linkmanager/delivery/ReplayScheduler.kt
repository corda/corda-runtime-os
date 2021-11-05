package net.corda.p2p.linkmanager.delivery

import net.corda.lifecycle.Lifecycle
import net.corda.v5.base.util.contextLogger
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * This class keeps track of messages which may need to be replayed.
 */
class ReplayScheduler<M>(
    private val replayPeriod: Duration,
    private val replayMessage: (message: M) -> Unit,
    private val currentTimestamp: () -> Long = { Instant.now().toEpochMilli() }
) : Lifecycle {

    @Volatile
    private var running = false
    private val startStopLock = ReentrantReadWriteLock()

    private lateinit var executorService: ScheduledExecutorService
    private val replayFutures = ConcurrentHashMap<String, ScheduledFuture<*>>()

    companion object {
        private val logger = contextLogger()
    }

    override val isRunning: Boolean
        get() = running

    override fun start() {
        startStopLock.write {
            if (!running) {
                executorService = Executors.newSingleThreadScheduledExecutor()
                running = true
            }
        }
    }

    override fun stop() {
        startStopLock.write {
            if (running) {
                executorService.shutdown()
                running = false
            }
        }
    }

    fun addForReplay(originalAttemptTimestamp: Long, uniqueId: String, message: M) {
        startStopLock.read {
            if (!running) {
                throw IllegalStateException("A message was added for replay before the ReplayScheduler was started.")
            }
            val delay = replayPeriod.toMillis() + originalAttemptTimestamp - currentTimestamp()
            val future = executorService.schedule({ replay(message, uniqueId) }, delay, TimeUnit.MILLISECONDS)
            replayFutures[uniqueId] = future
        }
    }

    fun removeFromReplay(uniqueId: String) {
        val removedFuture = replayFutures.remove(uniqueId)
        removedFuture?.cancel(false)
    }

    private fun replay(message: M, uniqueId: String) {
        try {
            replayMessage(message)
        } catch (exception: Exception) {
            logger.error("An exception was thrown when replaying a message. The task will be retried again in " +
                "${replayPeriod.toMillis()} ms.\nException:",
                exception
            )
        }
        reschedule(message, uniqueId)
    }

    private fun reschedule(message: M, uniqueId: String) {
        replayFutures.computeIfPresent(uniqueId) { _, _ ->
            executorService.schedule({ replay(message, uniqueId) }, replayPeriod.toMillis(), TimeUnit.MILLISECONDS)
        }
    }
}