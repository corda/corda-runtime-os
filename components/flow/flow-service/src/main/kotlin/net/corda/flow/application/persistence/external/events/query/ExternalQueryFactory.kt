package net.corda.flow.application.persistence.external.events.query

import co.paralleluniverse.fibers.Suspendable
import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.flow.fiber.FlowFiberSerializationService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [ExternalQueryFactory::class])
class ExternalQueryFactory @Activate constructor(
    @Reference(service = ExternalEventExecutor::class)
    private val externalEventExecutor: ExternalEventExecutor,
    @Reference(service = FlowFiberSerializationService::class)
    private val flowFiberSerializationService: FlowFiberSerializationService,
) {

    /**
     * Create a parameterised query object to execute named queries against the external DB process.
     * Sets default values of offset to 0, and limit to max int max int value. Sets query params to empty.
     * @param queryName the name of the query to execute
     * @return NamedParameterisedQuery object that can be used to execute named queries.
     */
    @Suppress("Unused")
    fun <R : Any> createNamedParameterisedQuery(queryName: String, expectedClass: Class<R>): NamedParameterisedQuery<R> {
        return NamedParameterisedQuery(
            externalEventExecutor,
            flowFiberSerializationService,
            queryName,
            mutableMapOf(),
            0,
            Int.MAX_VALUE,
            expectedClass
        )
    }

    /**
     * Create a paged find query object to execute Find queries against the external DB process.
     * Sets default values of offset to 0, and limit to max int max int value.
     * @param entityClass the class of the object to find
     * @return PagedFindQuery object that can be used to execute queries.
     */
    @Suspendable
    fun <R : Any> createPagedFindQuery(entityClass: Class<R>): PagedFindQuery<R> {
        return PagedFindQuery(externalEventExecutor, flowFiberSerializationService, entityClass, 0, Int.MAX_VALUE)
    }
}