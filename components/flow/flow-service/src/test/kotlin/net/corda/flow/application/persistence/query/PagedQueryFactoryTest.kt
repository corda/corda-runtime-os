package net.corda.flow.application.persistence.query

import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.flow.persistence.query.ResultSetFactory
import net.corda.v5.application.persistence.PagedQuery
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class PagedQueryFactoryTest {

    private class TestObject

    private val externalEventExecutor = mock<ExternalEventExecutor>()
    private val resultSetFactory = mock<ResultSetFactory>()
    private val resultSet = mock<PagedQuery.ResultSet<Any>>()

    private val pagedQueryFactory = PagedQueryFactoryImpl(externalEventExecutor, resultSetFactory)

    @BeforeEach
    fun beforeEach() {
        whenever(resultSetFactory.create(any(), any(), any(), any<Class<Any>>(), any())).thenReturn(resultSet)
        whenever(resultSet.next()).thenReturn(emptyList())
    }

    @Test
    fun `creates a named query with default values for limit, offset and parameters`() {
        val query = pagedQueryFactory.createNamedParameterizedQuery("query", TestObject::class.java)
        query.execute()
        verify(resultSetFactory).create(eq(emptyMap()), eq(Int.MAX_VALUE), eq(0), any<Class<Any>>(), any())
    }

    @Test
    fun `creates a find paged query with default values for limit and offset`() {
        val query = pagedQueryFactory.createPagedFindQuery(TestObject::class.java)
        query.execute()
        verify(resultSetFactory).create(eq(emptyMap()), eq(Int.MAX_VALUE), eq(0), any<Class<Any>>(), any())
    }
}