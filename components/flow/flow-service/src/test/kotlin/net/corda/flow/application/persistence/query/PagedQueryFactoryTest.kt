package net.corda.flow.application.persistence.query

import java.nio.ByteBuffer
import net.corda.flow.application.persistence.external.events.AbstractPersistenceExternalEventFactory
import net.corda.flow.application.persistence.external.events.FindAllParameters
import net.corda.flow.application.persistence.external.events.NamedQueryParameters
import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.flow.fiber.FlowFiberSerializationService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class PagedQueryFactoryTest {

    private class TestObject

    private val externalEventExecutor = mock<ExternalEventExecutor>()
    private val flowFiberSerializationService = mock<FlowFiberSerializationService>()

    private val pagedQueryFactory = PagedQueryFactory(externalEventExecutor, flowFiberSerializationService)

    @Test
    fun `creates a named query with default values for limit, offset and parameters`() {
        val parametersArgumentCaptor = argumentCaptor<NamedQueryParameters>()

        whenever(
            externalEventExecutor.execute(
                any<Class<out AbstractPersistenceExternalEventFactory<Any>>>(),
                parametersArgumentCaptor.capture()
            )
        ).thenReturn(listOf(ByteBuffer.wrap("bytes".toByteArray())))

        val query = pagedQueryFactory.createNamedParameterizedQuery("query", TestObject::class.java)

        query.execute()

        assertEquals(Int.MAX_VALUE, parametersArgumentCaptor.firstValue.limit)
        assertEquals(0, parametersArgumentCaptor.firstValue.offset)
        assertEquals(emptyMap<String, ByteBuffer>(), parametersArgumentCaptor.firstValue.parameters)
    }

    @Test
    fun `creates a find paged query with default values for limit and offset`() {
        val parametersArgumentCaptor = argumentCaptor<FindAllParameters>()

        assertThat(pagedQueryFactory.createPagedFindQuery(TestObject::class.java)::class.java)
            .isEqualTo(PagedFindQuery::class.java)

        whenever(
            externalEventExecutor.execute(
                any<Class<out AbstractPersistenceExternalEventFactory<Any>>>(),
                parametersArgumentCaptor.capture()
            )
        ).thenReturn(listOf(ByteBuffer.wrap("bytes".toByteArray())))

        val query = pagedQueryFactory.createPagedFindQuery(TestObject::class.java)

        query.execute()

        assertEquals(Int.MAX_VALUE, parametersArgumentCaptor.firstValue.limit)
        assertEquals(0, parametersArgumentCaptor.firstValue.offset)
    }
}