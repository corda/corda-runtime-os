package net.corda.messaging.db.sync

import net.corda.lifecycle.LifeCycle
import net.corda.messaging.db.persistence.DBAccessProvider
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import org.slf4j.Logger
import java.time.Duration
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * This component manages a set of [OffsetTracker]s, one per topic.
 * It makes sure they are initialised properly with the right offsets and then delegates calls to the right tracker.
 */
class OffsetTrackersManager(private val dbAccessProvider: DBAccessProvider): LifeCycle {

    companion object {
        private val log: Logger = contextLogger()
    }

    private val offsetTrackerPerTopic = mutableMapOf<String, OffsetTracker>()

    private var running = false
    private val startStopLock = ReentrantLock()

    override val isRunning: Boolean
        get() = running

    override fun start() {
        startStopLock.withLock {
            if (!running) {
                initialiseOffsetTrackers()
                offsetTrackerPerTopic.forEach { (_, offsetTracker) -> offsetTracker.start() }
                running = true
                log.debug { "Offset trackers manager started. Offset trackers initialised for topics: ${offsetTrackerPerTopic.keys}" }
            }
        }
    }

    override fun stop() {
        startStopLock.withLock {
            if (running) {
                offsetTrackerPerTopic.forEach { (_, offsetTracker) -> offsetTracker.stop() }
                running = false
                log.debug { "Offset trackers manager stopped." }
            }
        }
    }

    fun maxVisibleOffset(topic: String): Long {
        return offsetTrackerPerTopic[topic]!!.maxVisibleOffset()
    }

    fun waitForOffset(topic: String, offset: Long, duration: Duration): Boolean {
        return offsetTrackerPerTopic[topic]!!.waitForOffset(offset, duration)
    }

    fun getNextOffset(topic: String): Long {
        return offsetTrackerPerTopic[topic]!!.getNextOffset()
    }

    fun offsetReleased(topic: String, newOffset: Long) {
        offsetTrackerPerTopic[topic]!!.offsetReleased(newOffset)
    }

    private fun initialiseOffsetTrackers() {
        val maxOffsetPerTopic = dbAccessProvider.getMaxOffsetPerTopic()

        maxOffsetPerTopic.forEach { (topicName, offset) ->
            val offsetTracker = OffsetTracker(topicName,offset ?: 0)
            offsetTrackerPerTopic[topicName] = offsetTracker
        }
    }

}