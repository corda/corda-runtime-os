package net.corda.p2p.linkmanager.delivery

import net.corda.lifecycle.Lifecycle
import net.corda.v5.base.exceptions.CordaRuntimeException
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * This class keeps track of messages which may need to be replayed.
 */
class ReplayScheduler<REPLAY_ARGUMENT>(
    private val replayPeriod: Long,
    private val replayMessage: (argument: REPLAY_ARGUMENT) -> Unit,
    private val timestamp: () -> Long = { Instant.now().toEpochMilli() }
) : Lifecycle {

    @Volatile
    private var running = false
    private val startStopLock = ReentrantLock()

    private lateinit var executorService: ScheduledExecutorService
    private val replayFutures = ConcurrentHashMap<String, ScheduledFuture<*>>()

    override val isRunning: Boolean
        get() = running

    override fun start() {
        startStopLock.withLock{
            if (!running) {
                executorService = Executors.newSingleThreadScheduledExecutor()
                running = true
            }
        }
    }

    override fun stop() {
        startStopLock.withLock{
            if (running) {
                executorService.shutdown()
                running = false
            }
        }
    }

    fun addForReplay(timestamp: Long, uniqueId: String, replayArgument: REPLAY_ARGUMENT) {
        if (!running) {
            throw TaskAddedForReplayWhenNotStartedException()
        }
        val delay = replayPeriod + timestamp() - timestamp
        val future = executorService.scheduleAtFixedRate({ replayMessage(replayArgument) }, delay, replayPeriod, TimeUnit.MILLISECONDS)
        replayFutures[uniqueId] = future
    }

    fun removeFromReplay(uniqueId: String) {
        replayFutures[uniqueId]?.cancel(false)
    }

    class TaskAddedForReplayWhenNotStartedException:
        CordaRuntimeException("A task was added for replay before the ReplayScheduler was started.")
}