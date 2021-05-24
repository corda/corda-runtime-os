package net.corda.messaging.db.sync

import net.corda.messaging.db.publisher.DBPublisher
import net.corda.messaging.db.subscription.DBDurableSubscription
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * This class can be used by a [DBPublisher] to signal to a [DBDurableSubscription]
 * that a sufficient amount of records have arrived for subscription.
 *
 * The subscription can calculate the desired offset (depending on the number of records needed)
 * and wait until the publisher has signalled it reached it.
 */
class OffsetTracker {

    private var latestOffsetPerTopic: ConcurrentHashMap<String, AtomicLong> = ConcurrentHashMap()

    private val awaitedOffsetPerTopic: ConcurrentHashMap<String, Long> = ConcurrentHashMap()
    private val latchPerTopic: ConcurrentHashMap<String, CountDownLatch> = ConcurrentHashMap()
    private val awaitedOffsetLockPerTopic: ConcurrentHashMap<String, ReentrantReadWriteLock> = ConcurrentHashMap()

    /**
     * Waits until the offset has advanced up to [offset] or [duration] has elapsed.
     * @return true if the offset has been advanced up to specified one or false if the specified timeout has elapsed.
     *
     * This method is not thread-safe.
     * There can only be one subscription at a time, so it is expected to be called from that single subscription, not concurrently.
     */
    fun waitForOffset(topic: String, offset: Long, duration: Duration): Boolean {
        val latestOffset = latestOffsetPerTopic[topic]
        if (latestOffset != null && latestOffset.get() >= offset) {
            return true
        }

        val lock = awaitedOffsetLockPerTopic.computeIfAbsent(topic) { ReentrantReadWriteLock(true) }
        val latch = CountDownLatch(1)
        lock.write {
            awaitedOffsetPerTopic[topic] = offset
            latchPerTopic[topic] = latch

        }

        return latch.await(duration.toMillis(), TimeUnit.MILLISECONDS)
    }

    /**
     * Advances the offset to the specified value for the given topic.
     * If the offset has already been advanced to higher values than this, it's a no-op.
     * When this method is invoked, it also checks if there are subscribers waiting for a specific offset for that topic.
     * If so and the offset has been reached, it notifies them.
     *
     * This method is thread-safe.
     * It can potentially be called from multiple producers writing records and attempting to advance the offset.
     */
    fun advanceOffset(topic: String, newOffset: Long) {
        latestOffsetPerTopic.computeIfAbsent(topic) { AtomicLong(newOffset) }

        val topicCounter = latestOffsetPerTopic[topic]!!
        var currentOffset = topicCounter.get()
        var newOffsetRegistered = false
        while (newOffset > currentOffset && !newOffsetRegistered) {
            newOffsetRegistered = topicCounter.compareAndSet(currentOffset, newOffset)
            if (!newOffsetRegistered) {
                currentOffset = topicCounter.get()
            }
        }

        awaitedOffsetLockPerTopic[topic]?.read {
            val awaitedOffset = awaitedOffsetPerTopic[topic]
            if (awaitedOffset != null && currentOffset >= awaitedOffset) {
                latchPerTopic[topic]!!.countDown()
            }
        }
    }

}