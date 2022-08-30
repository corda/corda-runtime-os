package net.corda.flow.application.persistence.external.events.query

import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.flow.fiber.FlowFiberSerializationService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class ExternalQueryFactoryTest {
    private class TestObject
    private val externalEventExecutor = mock<ExternalEventExecutor>()
    private val flowFiberSerializationService = mock<FlowFiberSerializationService>()
    private val externalQueryFactory = ExternalQueryFactory(externalEventExecutor, flowFiberSerializationService)

    @Test
    fun `test create named query`() {
        assertThat(externalQueryFactory.createNamedParameterisedQuery("query", TestObject::class.java)::class.java)
            .isEqualTo(NamedParameterisedQuery::class.java)
    }

    @Test
    fun `test create find paged query`() {
        assertThat(externalQueryFactory.createPagedFindQuery(TestObject::class.java)::class.java).isEqualTo(PagedFindQuery::class.java)
    }
}