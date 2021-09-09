package net.corda.p2p.linkmanager.delivery

import net.corda.p2p.linkmanager.utilities.LoggingInterceptor
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.util.*
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
        assertThrows<MessageAddedForReplayWhenNotStartedException> {
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

        val tracker = TwoPhaseTrackReplayedMessages(messages, 4)

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

        tracker.firstPhaseWaitLatch.await()
        //Acknowledge all even messages
        for (id in messageIdsToRemove) {
            replayManager.removeFromReplay(id)
        }
        tracker.secondPhaseStartLatch.countDown()

        tracker.secondPhaseWaitLatch.await()
        Assertions.assertArrayEquals(messageIdsToNotRemove.toTypedArray(), tracker.replayedMessageIds.keys.toTypedArray())

        replayManager.stop()
    }

    @Test
    fun `The ReplayScheduler handles exceptions`() {
        val throwOnFirstReplay = ThrowExceptionOnFirstReplay()
        val replayManager = ReplayScheduler(replayPeriod, throwOnFirstReplay::replayMessage) { 0 }
        replayManager.start()
        replayManager.addForReplay(0, "", Any())
        throwOnFirstReplay.await()
        loggingInterceptor.assertErrorContains(
            "An exception was thrown when replaying a message. The task will be retired again in ${replayPeriod.toMillis()} ms.")
        replayManager.stop()
    }

    class TrackReplayedMessages(numUniqueMessages: Int) {
        private val latch = CountDownLatch(numUniqueMessages)
        private val replayedMessageIds = mutableSetOf<String>()

        fun replayMessage(messageId: String) {
            if (replayedMessageIds.contains(messageId)) return
            replayedMessageIds += messageId
            latch.countDown()
        }

        fun await() {
            latch.await()
        }
    }

    class ThrowExceptionOnFirstReplay {
        private val latch = CountDownLatch(2)
        private var shouldThrow = true

        @Suppress("UNUSED_PARAMETER")
        fun replayMessage(arg: Any) {
            latch.countDown()
            if (shouldThrow) {
                shouldThrow = false
                throw MyException()
            }
        }

        class MyException: Exception("Ohh No")

        fun await() {
            latch.await()
        }
    }

    class TwoPhaseTrackReplayedMessages(private val firstNumUniqueMessages: Int, private val secondNumUniqueMessages: Int) {
        val firstPhaseWaitLatch = CountDownLatch(1)
        val secondPhaseStartLatch = CountDownLatch(1)
        val secondPhaseWaitLatch = CountDownLatch(1)
        val replayedMessageIds = mutableMapOf<String, Int>()

        private var secondPhase = false

        fun replayMessage(messageId: String) {
            val replays = replayedMessageIds[messageId]
            if (replays == null) {
                replayedMessageIds[messageId] = 1
            } else {
                replayedMessageIds[messageId] = replays + 1
            }
            if (replayedMessageIds.keys.size == firstNumUniqueMessages) {
                replayedMessageIds.clear()
                firstPhaseWaitLatch.countDown()
                secondPhaseStartLatch.await()
                secondPhase = true
            }
            if (secondPhase && replayedMessageIds.keys.size == secondNumUniqueMessages) {
                secondPhaseWaitLatch.countDown()
            }
        }
    }
}