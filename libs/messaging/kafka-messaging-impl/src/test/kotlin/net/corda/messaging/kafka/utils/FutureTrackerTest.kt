package net.corda.messaging.kafka.subscription.net.corda.messaging.kafka.utils

import net.corda.messaging.api.exception.CordaRPCAPISenderException
import net.corda.messaging.kafka.utils.FutureTracker
import net.corda.test.util.eventually
import net.corda.v5.base.concurrent.getOrThrow
import net.corda.v5.base.util.millis
import net.corda.v5.base.util.seconds
import org.apache.kafka.common.TopicPartition
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.fail
import java.util.concurrent.CompletableFuture

@Suppress("ExplicitGarbageCollectionCall", "TooGenericExceptionCaught")
class FutureTrackerTest {

    private lateinit var tracker: FutureTracker<String>
    private lateinit var future: CompletableFuture<String>

    @BeforeEach
    fun before(){
        tracker = FutureTracker()
        future = CompletableFuture()
    }

    @Test
    fun `test FutureTracker partition creation, future insertion, retrieval and removal`() {
        tracker.addPartitions(listOf(TopicPartition("", 0)))
        tracker.addFuture("test", future, 0)
        Assertions.assertEquals(future, tracker.getFuture("test", 0))

        tracker.removeFuture("test", 0)
        Assertions.assertNull(tracker.getFuture("test", 0))
    }

    @Test
    fun `test FutureTracker partition removal `() {
        tracker.addPartitions(listOf(TopicPartition("", 0)))
        tracker.addPartitions(listOf(TopicPartition("", 1)))
        tracker.addFuture("test", future, 0)
        tracker.addFuture("test", future, 1)

        Assertions.assertEquals(future, tracker.getFuture("test", 0))
        Assertions.assertEquals(future, tracker.getFuture("test", 1))

        tracker.removePartitions(listOf(TopicPartition("", 1)))

        Assertions.assertEquals(future, tracker.getFuture("test", 0))
        Assertions.assertNull(tracker.getFuture("test", 1))
    }

    @Test
    fun `test adding a future to a non existent partition`() {
        tracker.addFuture("test", future, 0)
        Assertions.assertNull(tracker.getFuture("test", 0))
        assertThrows<CordaRPCAPISenderException> { future.getOrThrow() }
    }

    @Test
    fun `test removing future from a non existent partition`() {
        try {
            tracker.removeFuture("test", 0)
        } catch (ex: Exception) {
            fail("Nothing bad should have happened", ex)
        }
    }

    @Test
    fun `test retrieval of an non existent future`() {
        Assertions.assertNull(tracker.getFuture("test", 1))
    }

    @Test
    fun `test retrieval with the wrong correlation ID and partition`() {
        tracker.addPartitions(listOf(TopicPartition("", 0)))
        tracker.addFuture("test", future, 0)

        Assertions.assertEquals(future, tracker.getFuture("test", 0))
        Assertions.assertNull(tracker.getFuture("test2", 0))
        Assertions.assertNull(tracker.getFuture("test", 1))
    }

    @Test
    fun `test retrieval of a completed future and a discarded one`() {
        tracker.addPartitions(listOf(TopicPartition("", 0)))
        tracker.addFuture("test", future, 0)

        future.complete("It's done")
        //reassign to orphan the completed future
        future = CompletableFuture()
        System.gc()

        eventually(waitBetween = 10.millis, waitBefore = 0.millis, duration = 5.seconds) {
            Assertions.assertNull(tracker.getFuture("test", 0))
        }

        tracker.addFuture("test", future, 0)
        Assertions.assertEquals(future, tracker.getFuture("test", 0))

        //reassign to replicate a dropped incomplete future
        future = CompletableFuture()
        System.gc()

        eventually(waitBetween = 10.millis, waitBefore = 0.millis, duration = 5.seconds) {
            Assertions.assertNull(tracker.getFuture("test", 0))
        }
    }
}