package net.corda.flow.application.persistence.query

import co.paralleluniverse.fibers.Suspendable
import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.flow.fiber.FlowFiberSerializationService
import net.corda.v5.application.persistence.PagedQuery
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [PagedQueryFactory::class])
class PagedQueryFactory @Activate constructor(
    @Reference(service = ExternalEventExecutor::class)
    private val externalEventExecutor: ExternalEventExecutor,
    @Reference(service = FlowFiberSerializationService::class)
    private val flowFiberSerializationService: FlowFiberSerializationService,
) {

    /**
     * Create a [NamedParameterisedQuery] to execute named queries.
     *
     * Sets default values of [PagedQuery] offset to 0, and [PagedQuery] limit to [Int.MAX_VALUE].
     *
     * @param queryName The name of the query to execute.
     * @param expectedClass The type that the named query returns and is deserialized into.
     *
     * @return [NamedParameterisedQuery] instance that can be used to execute queries.
     */
    @Suppress("Unused")
    fun <R : Any> createNamedParameterizedQuery(
        queryName: String,
        expectedClass: Class<R>
    ): NamedParameterisedQuery<R> {
        return NamedParameterisedQuery(
            externalEventExecutor = externalEventExecutor,
            flowFiberSerializationService = flowFiberSerializationService,
            queryName = queryName,
            parameters = mutableMapOf(),
            limit = Int.MAX_VALUE,
            offset = 0,
            expectedClass = expectedClass
        )
    }

    /**
     * Create a [PagedFindQuery] to execute find queries.
     *
     * Sets default values of [PagedQuery] offset to 0, and [PagedQuery] limit to [Int.MAX_VALUE].
     *
     * @param entityClass The type to find.
     *
     * @return [PagedFindQuery] instance that can be used to execute queries.
     */
    @Suspendable
    fun <R : Any> createPagedFindQuery(entityClass: Class<R>): PagedFindQuery<R> {
        return PagedFindQuery(
            externalEventExecutor = externalEventExecutor,
            flowFiberSerializationService = flowFiberSerializationService,
            entityClass = entityClass,
            limit = Int.MAX_VALUE,
            offset = 0
        )
    }
}