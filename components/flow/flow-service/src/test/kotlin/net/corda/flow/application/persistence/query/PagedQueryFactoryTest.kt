package net.corda.flow.application.persistence.query

import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.flow.fiber.FlowFiberSerializationService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class PagedQueryFactoryTest {

    private class TestObject

    private val externalEventExecutor = mock<ExternalEventExecutor>()
    private val flowFiberSerializationService = mock<FlowFiberSerializationService>()
    private val pagedQueryFactory = PagedQueryFactory(externalEventExecutor, flowFiberSerializationService)

    @Test
    fun `create named query`() {
        assertThat(pagedQueryFactory.createNamedParameterisedQuery("query", TestObject::class.java)::class.java)
            .isEqualTo(NamedParameterisedQuery::class.java)
    }

    @Test
    fun `create find paged query`() {
        assertThat(pagedQueryFactory.createPagedFindQuery(TestObject::class.java)::class.java)
            .isEqualTo(PagedFindQuery::class.java)
    }
}