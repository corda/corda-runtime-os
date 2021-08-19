package net.corda.p2p.linkmanager.delivery

import net.corda.lifecycle.Lifecycle
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentSkipListMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * This class keeps track of messages which may need to be replayed.
 * [replayPeriod]
 */
class ReplayScheduler<REPLAY_ARGUMENT>(
    private val replayPeriod: Long,
    private val replayMessage: (argument: REPLAY_ARGUMENT) -> Unit,
    private val timestamp: () -> Long = { Instant.now().toEpochMilli() }
) : Lifecycle {

    @Volatile
    private var running = false
    private val startStopLock = ReentrantLock()

    private val executorService = Executors.newSingleThreadScheduledExecutor()

    private val pendingAckTimestamps = ConcurrentSkipListMap<SkipListKey, REPLAY_ARGUMENT>()

    private val keyFromMessageId = ConcurrentHashMap<String, SkipListKey>()

    //We can't have duplicate keys in the ConcurrentSkipListMap but want to order messages by timestamp
    private data class SkipListKey(val timestamp: Long, val messageId: String): Comparable<SkipListKey> {
        @Override
        override fun compareTo(other: SkipListKey): Int {
            val compareTimestamp = timestamp.compareTo(other.timestamp)
            if (compareTimestamp > 0 || compareTimestamp < 0) {
                return compareTimestamp
            }
            return messageId.compareTo(other.messageId)
        }
    }

    override val isRunning: Boolean
        get() = running

    override fun start() {
        startStopLock.withLock{
            if (!running) {
                executorService.scheduleAtFixedRate({
                    replayMessages()
                }, replayPeriod, replayPeriod, TimeUnit.MILLISECONDS)
                running = true
            }
        }
    }

    override fun stop() {
        if (running) {
            executorService.shutdown()
            running = false
        }
    }

    private fun replayMessages() {
        val startTimestamp = timestamp()
        val replayWindowMax = startTimestamp - replayPeriod
        for ((key, position) in pendingAckTimestamps) {
            if (key.timestamp > replayWindowMax) break
            replayMessage(position)
        }
    }

    fun addForReplay(uniqueId: String, replayArgument: REPLAY_ARGUMENT) {
        val timestamp = timestamp()
        val key = SkipListKey(timestamp, uniqueId)
        pendingAckTimestamps[key] = replayArgument
        keyFromMessageId[uniqueId] = key
    }

    fun removeFromReplay(uniqueId: String) {
        val key = keyFromMessageId[uniqueId]
        pendingAckTimestamps.remove(key)
    }
}