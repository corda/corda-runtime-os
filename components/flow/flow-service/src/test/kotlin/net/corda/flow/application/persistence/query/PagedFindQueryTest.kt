package net.corda.flow.application.persistence.query

import net.corda.flow.application.persistence.external.events.FindAllExternalEventFactory
import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.flow.persistence.query.OffsetResultSetExecutor
import net.corda.flow.persistence.query.ResultSetFactory
import net.corda.v5.application.persistence.CordaPersistenceException
import net.corda.v5.application.persistence.PagedQuery
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class PagedFindQueryTest {

    private companion object {
        val results = listOf("A", "B")
    }

    private val externalEventExecutor = mock<ExternalEventExecutor>()
    private val resultSetFactory = mock<ResultSetFactory>()
    private val resultSet = mock<PagedQuery.ResultSet<Any>>()
    private val resultSetExecutorCaptor = argumentCaptor<OffsetResultSetExecutor<Any>>()

    private val query = PagedFindQuery(
        externalEventExecutor = externalEventExecutor,
        resultSetFactory = resultSetFactory,
        limit = 1,
        offset = 0,
        entityClass = Any::class.java
    )

    @BeforeEach
    fun beforeEach() {
        whenever(resultSetFactory.create(any(), any(), any(), any(), resultSetExecutorCaptor.capture())).thenReturn(resultSet)
        whenever(resultSet.next()).thenReturn(results)
    }

    @Test
    fun `setLimit updates the limit`() {
        query.execute()
        verify(resultSetFactory).create(any(), eq(1), any(), any<Class<Any>>(), any())

        query.setLimit(10)
        query.execute()
        verify(resultSetFactory).create(any(), eq(10), any(), any<Class<Any>>(), any())
    }

    @Test
    fun `setOffset updates the offset`() {
        query.execute()
        verify(resultSetFactory).create(any(), any(), eq(0), any<Class<Any>>(), any())

        query.setOffset(10)
        query.execute()
        verify(resultSetFactory).create(any(), any(), eq(10), any<Class<Any>>(), any())
    }

    @Test
    fun `setLimit cannot be negative`() {
        assertThatThrownBy { query.setLimit(-1) }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `setLimit cannot be zero`() {
        assertThatThrownBy { query.setLimit(0) }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `setOffset cannot be negative`() {
        assertThatThrownBy { query.setOffset(-1) }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `execute creates a result set, gets the next page and returns the result set`() {
        assertThat(query.execute()).isEqualTo(resultSet)
        verify(resultSetFactory).create(eq(emptyMap()), any(), any(), any<Class<Any>>(), any())
        verify(resultSet).next()
    }

    @Test
    fun `rethrows CordaRuntimeExceptions as CordaPersistenceExceptions`() {
        whenever(externalEventExecutor.execute(any<Class<FindAllExternalEventFactory>>(), any()))
            .thenThrow(CordaRuntimeException("boom"))

        query.execute()

        val resultSetExecutor = resultSetExecutorCaptor.firstValue
        assertThatThrownBy { resultSetExecutor.execute(emptyMap(), 0) }.isInstanceOf(CordaPersistenceException::class.java)
    }

    @Test
    fun `does not rethrow general exceptions as CordaPersistenceExceptions`() {
        whenever(externalEventExecutor.execute(any<Class<FindAllExternalEventFactory>>(), any()))
            .thenThrow(IllegalStateException("boom"))

        query.execute()

        val resultSetExecutor = resultSetExecutorCaptor.firstValue
        assertThatThrownBy { resultSetExecutor.execute(emptyMap(), 0) }.isInstanceOf(IllegalStateException::class.java)
    }
}
