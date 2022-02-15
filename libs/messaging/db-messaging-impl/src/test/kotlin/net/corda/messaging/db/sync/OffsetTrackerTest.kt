package net.corda.messaging.db.sync

import net.corda.test.util.eventually
import net.corda.v5.base.util.millis
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.Callable
import java.util.concurrent.Executors

class OffsetTrackerTest {

    private val offsetTracker = OffsetTracker("test.topic", 3, 1, Executors.newSingleThreadScheduledExecutor(), 10.millis)

    private val executorService = Executors.newFixedThreadPool(10)

    @BeforeEach
    fun setup() {
        offsetTracker.start()
    }

    @AfterEach
    fun cleanup() {
        offsetTracker.stop()
    }

    @Test
    fun `when offsets are released in order, max visible offset is being advanced successfully`() {
        val firstOffset = offsetTracker.getNextOffset()
        val secondOffset = offsetTracker.getNextOffset()
        val thirdOffset = offsetTracker.getNextOffset()

        offsetTracker.offsetReleased(firstOffset)
        offsetTracker.offsetReleased(secondOffset)
        offsetTracker.offsetReleased(thirdOffset)

        assertThat(offsetTracker.maxVisibleOffset()).isEqualTo(thirdOffset)
    }

    @Test
    fun `when offsets are released out-of-order, max visible offset is being advanced successfully`() {
        val firstOffset = offsetTracker.getNextOffset()
        val secondOffset = offsetTracker.getNextOffset()
        val thirdOffset = offsetTracker.getNextOffset()

        offsetTracker.offsetReleased(secondOffset)
        offsetTracker.offsetReleased(thirdOffset)
        offsetTracker.offsetReleased(firstOffset)

        eventually(waitBetween = 100.millis, waitBefore = 0.millis) {
            assertThat(offsetTracker.maxVisibleOffset()).isEqualTo(thirdOffset)
        }
    }

    @Test
    fun `next offsets are allocated sequentially`() {
        val firstOffset = offsetTracker.getNextOffset()
        val secondOffset = offsetTracker.getNextOffset()
        val thirdOffset = offsetTracker.getNextOffset()

        assertThat(secondOffset).isEqualTo(firstOffset + 1)
        assertThat(thirdOffset).isEqualTo(secondOffset + 1)
    }

    @Test
    fun `if offset is not being advanced within the specified timeout, the awaiting method returns false`() {
        val newOffset = offsetTracker.getNextOffset()

        assertFalse(offsetTracker.waitForOffset(newOffset, 50.millis))
    }

    @Test
    fun `if offset has been advanced when wait method is invoked, the awaiting method returns true`() {
        val newOffset = offsetTracker.getNextOffset()
        offsetTracker.offsetReleased(newOffset)

        assertTrue(offsetTracker.waitForOffset(newOffset, 50.millis))
    }

    @Test
    fun `if offset gets advanced before the timeout expires, the awaiting method returns true`() {
        val newOffset = offsetTracker.getNextOffset()

        val waitResult = executorService.submit(Callable { offsetTracker.waitForOffset(newOffset, 200.millis) } )

        offsetTracker.offsetReleased(newOffset)

        assertTrue(waitResult.get())
    }

    @Test
    fun `when multiple offsets are allocated and released concurrently, the max visible offset is being advanced successfully`() {
        val offsets = (1..1_000).map { offsetTracker.getNextOffset() }.shuffled()
        val maxOffset = offsets.maxOrNull()!!

        for (offset in offsets) {
            executorService.submit { offsetTracker.offsetReleased(offset) }
        }

        assertTrue(offsetTracker.waitForOffset(maxOffset, 600.millis))
        assertThat(offsetTracker.maxVisibleOffset()).isEqualTo(maxOffset)
    }

}