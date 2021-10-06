package net.corda.messaging.kafka.subscription.net.corda.messaging.kafka.utils

import net.corda.messaging.kafka.utils.FutureTracker
import org.apache.kafka.common.TopicPartition
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture

class FutureTrackerTest {

    @Test
    fun `test FutureTracker future insertion, retrieval and removal`() {
        val tracker = FutureTracker<String>()
        val future = CompletableFuture<String>()

        tracker.addFuture("test", future, 0)
        Assertions.assertEquals(future, tracker.getFuture("test", 0))

        tracker.removeFuture("test", 0)
        Assertions.assertNull(tracker.getFuture("test", 0))
    }

    @Test
    fun `test FutureTracker partition removal `() {
        val tracker = FutureTracker<String>()
        val future = CompletableFuture<String>()

        tracker.addFuture("test", future, 0)
        tracker.addFuture("test", future, 1)

        Assertions.assertEquals(future, tracker.getFuture("test", 0))
        Assertions.assertEquals(future, tracker.getFuture("test", 1))

        tracker.removePartitions(listOf(TopicPartition(null, 1)))

        Assertions.assertEquals(future, tracker.getFuture("test", 0))
        Assertions.assertNull(tracker.getFuture("test", 1))
    }
}