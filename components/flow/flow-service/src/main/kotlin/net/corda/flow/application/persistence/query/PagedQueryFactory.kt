package net.corda.flow.application.persistence.query

import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.flow.persistence.query.ResultSetFactory
import net.corda.sandbox.type.SandboxConstants.CORDA_UNINJECTABLE_SERVICE
import net.corda.sandbox.type.UsedByFlow
import net.corda.v5.application.persistence.PagedQuery
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE
import org.osgi.service.component.propertytypes.ServiceRanking

interface PagedQueryFactory {
    /**
     * Create a [NamedParameterizedQuery] to execute named queries.
     *
     * Sets default values of [PagedQuery] offset to 0, and [PagedQuery] limit to [Int.MAX_VALUE].
     *
     * @param queryName The name of the query to execute.
     * @param expectedClass The type that the named query returns and is deserialized into.
     *
     * @return [NamedParameterizedQuery] instance that can be used to execute queries.
     */
    @Suppress("Unused")
    fun <R : Any> createNamedParameterizedQuery(
        queryName: String,
        expectedClass: Class<R>
    ): NamedParameterizedQuery<R>

    /**
     * Create a [PagedFindQuery] to execute find queries.
     *
     * Sets default values of [PagedQuery] offset to 0, and [PagedQuery] limit to [Int.MAX_VALUE].
     *
     * @param entityClass The type to find.
     *
     * @return [PagedFindQuery] instance that can be used to execute queries.
     */
    fun <R : Any> createPagedFindQuery(entityClass: Class<R>): PagedFindQuery<R>
}

/**
 * The [SerializationService] component is a system service, which means that
 * every sandbox will contain its own instance. Hence every sandbox must also
 * be able to contain its own [PagedQueryFactory] instance which uses that
 * [SerializationService].
 */
@Component(
    service = [ PagedQueryFactory::class, UsedByFlow::class ],
    property = [ CORDA_UNINJECTABLE_SERVICE ],
    scope = PROTOTYPE
)
internal class PagedQueryFactoryImpl @Activate constructor(
    @Reference(service = ExternalEventExecutor::class)
    private val externalEventExecutor: ExternalEventExecutor,
    @Reference(service = ResultSetFactory::class)
    private val resultSetFactory: ResultSetFactory
) : PagedQueryFactory, UsedByFlow {

    override fun <R : Any> createNamedParameterizedQuery(
        queryName: String,
        expectedClass: Class<R>
    ): NamedParameterizedQuery<R> {
        return NamedParameterizedQuery(
            externalEventExecutor = externalEventExecutor,
            resultSetFactory = resultSetFactory,
            queryName = queryName,
            parameters = mutableMapOf(),
            limit = Int.MAX_VALUE,
            offset = 0,
            expectedClass = expectedClass
        )
    }

    @Suspendable
    override fun <R : Any> createPagedFindQuery(entityClass: Class<R>): PagedFindQuery<R> {
        return PagedFindQuery(
            externalEventExecutor = externalEventExecutor,
            resultSetFactory = resultSetFactory,
            entityClass = entityClass,
            limit = Int.MAX_VALUE,
            offset = 0
        )
    }
}

/**
 * All components not inside a sandbox will use this [PagedQueryFactory] instance.
 * This instance should never be used in practice, and exists to alert you that
 * your sandbox services have resolved incorrectly.
 */
@Component(service = [ PagedQueryFactory::class ])
@ServiceRanking(1)
private class SingletonPagedQueryFactoryImpl @Activate constructor() : PagedQueryFactory {
    override fun <R : Any> createNamedParameterizedQuery(queryName: String, expectedClass: Class<R>): NamedParameterizedQuery<R> {
        throw UnsupportedOperationException("Service used outside of a sandbox")
    }

    override fun <R : Any> createPagedFindQuery(entityClass: Class<R>): PagedFindQuery<R> {
        throw UnsupportedOperationException("Service used outside of a sandbox")
    }
}
