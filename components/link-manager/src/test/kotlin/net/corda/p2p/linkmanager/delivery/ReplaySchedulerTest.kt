package net.corda.p2p.linkmanager.delivery

import net.corda.p2p.linkmanager.utilities.LoggingInterceptor
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch

class ReplaySchedulerTest {

    companion object {
        private val replayPeriod = Duration.ofMillis(2)
        lateinit var loggingInterceptor: LoggingInterceptor

        @BeforeAll
        @JvmStatic
        fun setup() {
            loggingInterceptor = LoggingInterceptor.setupLogging()
        }
    }

    @AfterEach
    fun resetLogging() {
        loggingInterceptor.reset()
    }

    @Test
    fun `The ReplayScheduler will not replay before start`() {
        val replayManager = ReplayScheduler(replayPeriod, { _: Any -> } ) { 0 }
        assertThrows<IllegalStateException> {
            replayManager.addForReplay(0,"", Any())
        }
    }

    @Test
    fun `The ReplayScheduler replays added messages`() {
        val messages = 9

        val tracker = TrackReplayedMessages(messages)

        val replayManager = ReplayScheduler(replayPeriod, tracker::replayMessage) { 0 }
        replayManager.start()
        for (i in 0 until messages) {
            val messageId = UUID.randomUUID().toString()
            replayManager.addForReplay(
                0,
                messageId,
                messageId
            )
        }

        tracker.await()
        replayManager.stop()
    }

    @Test
    fun `The ReplayScheduler doesn't replay removed messages`() {
        val messages = 8

        val tracker = TrackReplayedMessages(messages)

        val replayManager = ReplayScheduler(replayPeriod, tracker::replayMessage) { 0 }
        replayManager.start()

        val messageIdsToRemove = mutableListOf<String>()
        val messageIdsToNotRemove = mutableListOf<String>()
        for (i in 0 until messages) {
            val messageId = UUID.randomUUID().toString()
            if (i % 2 == 0) messageIdsToRemove.add(messageId)
            else messageIdsToNotRemove.add(messageId)
            replayManager.addForReplay(
                0,
                messageId,
                messageId
            )
        }

        tracker.await()
        //Acknowledge all even messages
        for (id in messageIdsToRemove) {
            replayManager.removeFromReplay(id)
        }

        //Wait some time to until the even messages should have stopped replaying
        Thread.sleep(2 * messages * replayPeriod.toMillis())
        val removedMessages = mutableMapOf<String, Int>()
        for (id in messageIdsToRemove) {
            removedMessages[id] = tracker.numberOfReplays[id]!!
        }

        //Wait again and check the number of replays for each stopped message is the same
        Thread.sleep(2 * messages * replayPeriod.toMillis())
        for (id in messageIdsToRemove) {
            assertEquals(removedMessages[id], tracker.numberOfReplays[id]!!)
        }
    }

    @Test
    fun `The ReplayScheduler handles exceptions`() {
        val message = "message"
        val tracker = TrackReplayedMessages(2, 1)
        val replayManager = ReplayScheduler(replayPeriod, tracker::replayMessage) { 0 }
        replayManager.start()
        replayManager.addForReplay(0, "", message)
        tracker.await()
        loggingInterceptor.assertErrorContains(
            "An exception was thrown when replaying a message. The task will be retried again in ${replayPeriod.toMillis()} ms.")
        replayManager.stop()
        assertTrue(tracker.numberOfReplays[message]!! >= 1)
    }

    class TrackReplayedMessages(numReplayedMessages: Int, private val totalNumberOfExceptions: Int = 0) {
        private val latch = CountDownLatch(numReplayedMessages)
        val numberOfReplays = ConcurrentHashMap<String, Int>()
        private var numberOfExceptions = 0

        fun replayMessage(message: String) {
            if (numberOfExceptions < totalNumberOfExceptions) {
                numberOfExceptions++
                latch.countDown()
                throw MyException()
            }
            numberOfReplays.compute(message) { _, numberOfReplays ->
                (numberOfReplays ?: 0) + 1
            }
            latch.countDown()
        }

        class MyException: Exception("Ohh No")

        fun await() {
            latch.await()
        }
    }
}