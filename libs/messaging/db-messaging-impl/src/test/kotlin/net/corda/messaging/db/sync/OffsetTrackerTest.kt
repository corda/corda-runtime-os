package net.corda.messaging.db.sync

import net.corda.v5.base.util.millis
import net.corda.v5.base.util.seconds
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.Callable
import java.util.concurrent.Executors

class OffsetTrackerTest {

    private val offsetTracker = OffsetTracker()

    private val executorService = Executors.newFixedThreadPool(10)

    private val topic1 = "test.topic1"
    private val topic2 = "test.topic2"


    @Test
    fun `if offset is not being advanced within the specified timeout, the awaiting method returns false`() {
        assertFalse(offsetTracker.waitForOffset(topic1, 5, 5.millis))

        offsetTracker.advanceOffset(topic1, 4)
        assertFalse(offsetTracker.waitForOffset(topic1, 5, 5.millis))
    }

    @Test
    fun `if offset has been advanced when wait method is invoked, the awaiting method returns true`() {
        offsetTracker.advanceOffset(topic1, 5)

        assertTrue(offsetTracker.waitForOffset(topic1, 5, 5.millis))
    }

    @Test
    fun `if offset gets advanced before the timeout expires, the awaiting method returns true`() {
        offsetTracker.advanceOffset(topic1 , 4)

        val waitResult = executorService.submit(Callable {offsetTracker.waitForOffset(topic1, 5, 10.millis) })
        offsetTracker.advanceOffset(topic1, 5)

        assertTrue(waitResult.get())
    }

    @Test
    fun `out of order invocations to advance the offsets are handled gracefully`() {
        offsetTracker.advanceOffset(topic1, 5)
        offsetTracker.advanceOffset(topic1, 4)

        assertTrue(offsetTracker.waitForOffset(topic1, 5, 5.millis))
    }

    @Test
    fun `advancing the offset for one topic does not affect offsets for other topics`() {
        offsetTracker.advanceOffset(topic1, 5)

        assertTrue(offsetTracker.waitForOffset(topic1, 5, 5.millis))
        assertFalse(offsetTracker.waitForOffset(topic2, 5, 5.millis))

        offsetTracker.advanceOffset(topic2, 8)

        assertTrue(offsetTracker.waitForOffset(topic2, 8, 5.millis))
        assertFalse(offsetTracker.waitForOffset(topic1, 8, 5.millis))
    }

    @Test
    fun `multi-threaded`() {
        val offsets = (1L..300L).toList().shuffled()
        for (offset in offsets) {
            executorService.submit { offsetTracker.advanceOffset(topic1, offset) }
        }

        assertTrue(offsetTracker.waitForOffset(topic1, 300, 5.seconds))
    }

}