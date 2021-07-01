package net.corda.messaging.db.sync

import com.google.common.util.concurrent.ThreadFactoryBuilder
import net.corda.lifecycle.LifeCycle
import net.corda.messaging.db.persistence.DBAccessProvider
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import org.slf4j.Logger
import java.time.Duration
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * This component manages a set of [OffsetTracker]s, one per topic.
 * It makes sure they are initialised properly with the right offsets and then delegates calls to the right tracker.
 */
class OffsetTrackersManager(private val dbAccessProvider: DBAccessProvider): LifeCycle {

    companion object {
        private val log: Logger = contextLogger()
        private const val SHARED_OFFSET_TRACKERS_POOL_SIZE_FOR = 5
    }

    private val offsetTrackerPerTopicPartition: MutableMap<String, MutableMap<Int, OffsetTracker>> = mutableMapOf()
    private lateinit var executorService: ScheduledExecutorService

    private var running = false
    private val startStopLock = ReentrantLock()

    override val isRunning: Boolean
        get() = running

    override fun start() {
        startStopLock.withLock {
            if (!running) {
                executorService = Executors.newScheduledThreadPool(
                    SHARED_OFFSET_TRACKERS_POOL_SIZE_FOR,
                    ThreadFactoryBuilder().setNameFormat("offset-tracker-background-task-%d").build()
                )
                initialiseOffsetTrackers()
                offsetTrackerPerTopicPartition.forEach { (_, offsetTrackerPerPartition) ->
                    offsetTrackerPerPartition.forEach { (_, offsetTracker) -> offsetTracker.start() }
                }
                running = true
                log.debug { "Offset trackers manager started. " +
                        "Offset trackers initialised for topics: ${offsetTrackerPerTopicPartition.keys}" }
            }
        }
    }

    override fun stop() {
        startStopLock.withLock {
            if (running) {
                offsetTrackerPerTopicPartition.forEach { (_, offsetTrackerPerPartition) ->
                    offsetTrackerPerPartition.forEach { (_, offsetTracker) -> offsetTracker.stop() }
                }
                running = false
                log.debug { "Offset trackers manager stopped." }
            }
        }
    }

    fun maxVisibleOffset(topic: String, partition: Int): Long {
        return offsetTrackerPerTopicPartition[topic]!![partition]!!.maxVisibleOffset()
    }

    fun waitForOffsets(topic: String, offsets: Map<Int, Long>, duration: Duration) {
        val finalTimeout = Instant.now() + duration
        offsets.forEach { (partition, offset) ->
            val waitTime = Duration.between(Instant.now(), finalTimeout)
            offsetTrackerPerTopicPartition[topic]!![partition]!!.waitForOffset(offset, waitTime)
        }
    }

    fun getNextOffset(topic: String, partition: Int): Long {
        return offsetTrackerPerTopicPartition[topic]!![partition]!!.getNextOffset()
    }

    fun offsetReleased(topic: String, partition:Int, newOffset: Long) {
        offsetTrackerPerTopicPartition[topic]!![partition]!!.offsetReleased(newOffset)
    }

    private fun initialiseOffsetTrackers() {
        val maxOffsetPerTopic = dbAccessProvider.getMaxOffsetsPerTopic()

        maxOffsetPerTopic.forEach { (topicName, maxOffsetsPerPartition) ->
            offsetTrackerPerTopicPartition[topicName] = mutableMapOf()
            maxOffsetsPerPartition.forEach { (partition, offset) ->
                val offsetTracker = OffsetTracker(topicName,partition, offset ?: 0, executorService)
                offsetTrackerPerTopicPartition[topicName]!![partition] = offsetTracker
            }
        }
    }

}