package net.corda.messaging.db.sync

import net.corda.lifecycle.LifeCycle
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.subscription.Subscription
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.v5.base.util.seconds
import org.slf4j.Logger
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * This class can be used to track maximum "visible" offset for a single topic.
 * A "visible" offset is an offset below which all records have been published and it means that all the offsets up to this one can
 * be safely exposed and processed by subscriptions.
 *
 * This component allocates offsets to [Publisher]s, which are then notifying it once these offsets have been published.
 * [Subscription]s can use this component to wait until a specific offset has become visible before attempting
 * to retrieve records up to this offset.
 *
 * @param initialMaxOffset is the maximum offset in the database, as retrieved during the startup.
 * @param executorService an executor service used to execute a background task that will check for lingering
 *                        offsets that block max visibile offset from being advanced. Applicable mostly to idle periods.
 * @param periodicChecksInterval the interval at which the background task will execute.
 */
class OffsetTracker(private val topic: String,
                    private val partition: Int,
                    initialMaxOffset: Long,
                    private val executorService: ScheduledExecutorService,
                    private val periodicChecksInterval: Duration = 2.seconds): LifeCycle {

    companion object {
        private val log: Logger = contextLogger()
    }

    private val maxVisibleOffset = AtomicLong(initialMaxOffset)
    private val nextOffset = AtomicLong(initialMaxOffset + 1)
    private val releasedInvisibleOffsets = ConcurrentHashMap.newKeySet<Long>()
    private val backlogProcessingLock = ReentrantLock()

    private val waitingList = ConcurrentHashMap<WaitingIdentifier, WaitingData>()

    private var backgroundTask: ScheduledFuture<*>? = null

    private var running = false
    private val startStopLock = ReentrantLock()

    override val isRunning: Boolean
        get() = running

    override fun start() {
        startStopLock.withLock {
            if (!running) {
                backgroundTask = executorService.scheduleWithFixedDelay({
                    /**
                     * there is a very rare case where a publisher might add an offset into the [releasedInvisibleOffsets]
                     * and not advance the offset even though it would be possible
                     * (due to race conditions with other publishers that were advancing the offset and just released the lock).
                     * Under normal conditions, subsequent publishers will advance the offset.
                     * However, if the traffic is extremely low and no new records are published after this point,
                     * this offset will remain "invisible" until the next record is published.
                     * This background thread tries to mitigate this by running once in a while and processing these lingering offsets.
                     */
                    processOffsetsBacklogAndNotifyWaiters(maxVisibleOffset() + 1)
                }, periodicChecksInterval.toMillis(), periodicChecksInterval.toMillis(), TimeUnit.MILLISECONDS)
                running = true
                log.debug { "Offset tracker for (topic $topic, partition $partition) started with max visible offset " +
                        "${maxVisibleOffset.get()} and next offset ${nextOffset.get()}." }
            }
        }
    }

    override fun stop() {
        startStopLock.withLock {
            if (running) {
                backgroundTask?.cancel(false)
                running = false
                log.debug { "Offset tracker for (topic $topic, partition $partition) stopped." }
            }
        }
    }

    /**
     * Returns the maximum visible offset.
     */
    fun maxVisibleOffset() = maxVisibleOffset.get()

    /**
     * Waits until the maximum visible offset has advanced up to [offset] or [duration] has elapsed.
     * @return true if the offset has been advanced up to the specified one or false if the specified timeout has elapsed.
     */
    fun waitForOffset(offset: Long, duration: Duration): Boolean {
        if (maxVisibleOffset() >= offset) {
            return true
        }

        val waitingIdentifier = UUID.randomUUID().toString()
        val latch = CountDownLatch(1)
        val waitingData = WaitingData(latch, offset)
        waitingList[waitingIdentifier] = waitingData

        val offsetReached = latch.await(duration.toMillis(), TimeUnit.MILLISECONDS)
        waitingList.remove(waitingIdentifier)

        return offsetReached
    }

    /**
     * Returns the next offset available for a new record.
     */
    fun getNextOffset() = nextOffset.getAndIncrement()

    /**
     * Signals that a specific offset has been released.
     * This means the record with this offset has either been published successfully to the database (transaction has been committed)
     * or the record will not be published (transaction has been rolled back).
     *
     * This method advances the maximum visible offset, when needed.
     * In the scenario of out-of-order publication, offsets are preserved in memory
     * until there are no gaps when the max visible offset will be advanced accordingly.
     */
    fun offsetReleased(newOffset: Long) {
        val maxVisibleOffsetUpdated = maxVisibleOffset.compareAndSet(newOffset-1, newOffset)

        if (!maxVisibleOffsetUpdated) {
            releasedInvisibleOffsets.add(newOffset)
            return
        }

        processOffsetsBacklogAndNotifyWaiters(newOffset + 1)
    }

    /**
     * Goes through the offsets in [releasedInvisibleOffsets] that had been released out-of-order and advances the max visible offset
     * up to the point where there are no gaps.
     *
     * It also goes through those waiting and notifies them if the max visible offset is higher than the offset they have been waiting for.
     *
     * Note that any processing is performed only after trying to acquire the [backlogProcessingLock].
     * The acquisition of the lock is optimistic, so that processing is performed only by one actor and none of the others needs to wait.
     *
     * @param startingOffset the offset from which the processing of the backlog will start.
     */
    private fun processOffsetsBacklogAndNotifyWaiters(startingOffset: Long) {
        if(backlogProcessingLock.tryLock()) {
            var currentOffset = startingOffset
            var advancingMaxVisibleOffset = true
            while (releasedInvisibleOffsets.contains(currentOffset) && advancingMaxVisibleOffset) {
                advancingMaxVisibleOffset = maxVisibleOffset.compareAndSet(currentOffset - 1, currentOffset)
                if (advancingMaxVisibleOffset) {
                    releasedInvisibleOffsets.remove(currentOffset)
                }
                currentOffset++
            }

            /**
             * Last, we notify any waiting readers, if the max visible offset is higher than the offset they have been waiting for.
             */
            for ((_, waitingData) in waitingList) {
                if (maxVisibleOffset.get() >= waitingData.awaitedOffset) {
                    waitingData.latch.countDown()
                }
            }

            backlogProcessingLock.unlock()
        }
    }

}

private typealias WaitingIdentifier = String
private data class WaitingData(val latch: CountDownLatch, val awaitedOffset: Long)