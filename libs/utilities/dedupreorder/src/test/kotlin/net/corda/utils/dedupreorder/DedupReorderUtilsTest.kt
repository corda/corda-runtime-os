package net.corda.utils.dedupreorder

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.lang.System.currentTimeMillis

class DedupReorderUtilsTest {

    private companion object {
        const val MESSAGE_VALID_WINDOW = 1000000L
        val MESSAGE_TIMESTAMP = currentTimeMillis()
    }

    var dedupReorderHelper: DedupReorderHelper<Int, Int> = mock()

    @BeforeEach
    fun setup() {
        whenever(dedupReorderHelper.getEventTimestampField(anyInt())).thenReturn(MESSAGE_TIMESTAMP)
        whenever(dedupReorderHelper.getEventSequenceNumber(anyInt())).thenAnswer { invocation ->
            invocation.arguments.first() as Int
        }
        whenever(dedupReorderHelper.getCurrentSequenceNumber(anyInt())).thenAnswer { invocation ->
            invocation.arguments.first() as Int
        }
    }

    @Test
    fun `test null state and sequence != 1`() {
        val dedupAndReorderProcessor = DedupReorderUtils(MESSAGE_VALID_WINDOW, dedupReorderHelper)
        val result = dedupAndReorderProcessor.getNextEvents(null,2)

        assertThat(result.first).isNull()
        assertThat(result.second).isEqualTo(listOf<Int>())
    }

    @Test
    fun `test null state and event expired`() {
        whenever(dedupReorderHelper.getEventTimestampField(anyInt())).thenReturn(Long.MIN_VALUE)

        val dedupAndReorderProcessor = DedupReorderUtils(MESSAGE_VALID_WINDOW, dedupReorderHelper)
        val result = dedupAndReorderProcessor.getNextEvents(null,1)

        assertThat(result.first).isNull()
        assertThat(result.second).isEqualTo(listOf<Int>())
    }

    @Test
    fun `test null state and valid event`() {
        whenever(dedupReorderHelper.updateState(null, 1, mutableListOf())).thenReturn(1)

        val dedupAndReorderProcessor = DedupReorderUtils(MESSAGE_VALID_WINDOW, dedupReorderHelper)
        val result = dedupAndReorderProcessor.getNextEvents(null, 1)

        assertThat(result.first).isEqualTo(1)
        assertThat(result.second).isEqualTo(listOf(1))
    }

    @Test
    fun `test sequence less than expected`() {
        whenever(dedupReorderHelper.updateState(2, 2, mutableListOf())).thenReturn(2)

        val dedupAndReorderProcessor = DedupReorderUtils(MESSAGE_VALID_WINDOW, dedupReorderHelper)
        val result = dedupAndReorderProcessor.getNextEvents(2,1)

        assertThat(result.first).isEqualTo(2)
        assertThat(result.second).isEqualTo(listOf<Int>())
    }

    @Test
    fun `test event sequence is expected next with additional expected buffered`() {
        val expectedOutputEvents = listOf(3,4,5)
        whenever(dedupReorderHelper.updateState(2, 5, mutableListOf(8, 9))).thenReturn(5)
        whenever(dedupReorderHelper.getOutOfOrderMessages(any())).thenReturn(mutableListOf(4, 5, 8, 9))

        val dedupAndReorderProcessor = DedupReorderUtils(MESSAGE_VALID_WINDOW, dedupReorderHelper)
        val result = dedupAndReorderProcessor.getNextEvents(2,3)

        assertThat(result.first).isEqualTo(5)
        assertThat(result.second).isEqualTo(expectedOutputEvents)
    }

    @Test
    fun `test event sequence is higher than expected with additional buffered`() {
        val expectedOutOfOrMessages = mutableListOf(4, 5, 6)
        whenever(dedupReorderHelper.updateState(2, 2, expectedOutOfOrMessages)).thenReturn(2)
        whenever(dedupReorderHelper.getOutOfOrderMessages(any())).thenReturn(mutableListOf(4, 5))

        val dedupAndReorderProcessor = DedupReorderUtils(MESSAGE_VALID_WINDOW, dedupReorderHelper)
        val result = dedupAndReorderProcessor.getNextEvents(2,6)

        assertThat(result.first).isEqualTo(2)
        assertThat(result.second).isEqualTo(listOf<Int>())
    }

}