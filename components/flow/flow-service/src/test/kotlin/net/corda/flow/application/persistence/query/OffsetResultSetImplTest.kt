package net.corda.flow.application.persistence.query

import net.corda.flow.persistence.query.OffsetResultSetExecutor
import net.corda.v5.application.serialization.SerializationService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer

class OffsetResultSetImplTest {

    private companion object {
        const val LIMIT = 2
        const val OFFSET = 0
        val serializedParameters = mapOf<String, ByteBuffer>("1" to ByteBuffer.wrap(byteArrayOf(1, 2, 3, 4)))
        val resultExecutorResults = OffsetResultSetExecutor.Results(
            listOf(
                byteArrayOf(5, 6, 7, 8),
                byteArrayOf(5, 6, 7, 8)
            ),
            LIMIT
        )
    }

    private val serializationService = mock<SerializationService>()
    private val resultSetExecutor = mock<OffsetResultSetExecutor<Any>>()

    private val resultSet = OffsetResultSetImpl(
        serializationService = serializationService,
        serializedParameters = serializedParameters,
        limit = LIMIT,
        offset = OFFSET,
        resultClass = Any::class.java,
        resultSetExecutor = resultSetExecutor
    )

    @BeforeEach
    fun beforeEach() {
        whenever(serializationService.deserialize(any<ByteArray>(), eq(Any::class.java))).thenReturn("A", "B")
    }

    @Test
    fun `getResults returns an empty list if next has not been called`() {
        assertThat(resultSet.results).isEmpty()
    }

    @Test
    fun `getResults returns the last retrieved page of data`() {
        whenever(resultSetExecutor.execute(serializedParameters, OFFSET)).thenReturn(resultExecutorResults)
        assertThat(resultSet.next()).isEqualTo(listOf("A", "B"))
    }

    @Test
    fun `hasNext returns true when next has not been called yet`() {
        assertThat(resultSet.hasNext()).isTrue
    }

    @Test
    fun `hasNext returns true when the number of rows returned from next is equal to the limit`() {
        whenever(resultSetExecutor.execute(serializedParameters, OFFSET)).thenReturn(resultExecutorResults)
        resultSet.next()
        assertThat(resultSet.hasNext()).isTrue
    }

    @Test
    fun `hasNext returns false when the number of rows returned from next is less than the limit`() {
        whenever(resultSetExecutor.execute(serializedParameters, OFFSET)).thenReturn(resultExecutorResults.copy(
            serializedResults = listOf(
                byteArrayOf(5, 6, 7, 8)
            ),
            numberOfRowsFromQuery = 12) // Let's say we filtered out 11
        )
        resultSet.next()
        assertThat(resultSet.hasNext()).isFalse
    }

    @Test
    fun `result set cannot be instantiated with zero limit`() {
        assertThrows<IllegalArgumentException> {
            this.resultSet.copy(limit = 0)
        }
    }

    @Test
    fun `result set cannot be instantiated with negative offset`() {
        assertThrows<IllegalArgumentException> {
            this.resultSet.copy(offset = -1)
        }
    }

    @Test
    fun `next retrieves the first page of data starting from the result set's original offset`() {
        whenever(resultSetExecutor.execute(serializedParameters, OFFSET)).thenReturn(resultExecutorResults)
        resultSet.next()
        verify(resultSetExecutor).execute(any(), eq(OFFSET))
    }

    @Test
    fun `next increments the offset by the limit's value each time it is called`() {
        whenever(resultSetExecutor.execute(eq(serializedParameters), any())).thenReturn(resultExecutorResults)
        resultSet.next()
        resultSet.next()
        resultSet.next()
        resultSetExecutor.inOrder {
            verify().execute(any(), eq(OFFSET))
            verify().execute(any(), eq(OFFSET + LIMIT))
            verify().execute(any(), eq(OFFSET + LIMIT + LIMIT))
        }
    }

    @Test
    fun `next throws exception when hasNext returns false`() {
        whenever(resultSetExecutor.execute(serializedParameters, OFFSET)).thenReturn(resultExecutorResults.copy(
            serializedResults = listOf(
                byteArrayOf(5, 6, 7, 8)
            ),
            numberOfRowsFromQuery = 1
        ))
        resultSet.next()
        assertThatThrownBy { resultSet.next() }.isInstanceOf(NoSuchElementException::class.java)
    }
}
