package net.corda.flow.application.persistence.query

import net.corda.flow.application.persistence.external.events.NamedQueryExternalEventFactory
import net.corda.flow.application.persistence.external.events.NamedQueryParameters
import net.corda.utilities.toByteArrays
import net.corda.flow.application.persistence.wrapWithPersistenceException
import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.flow.persistence.query.ResultSetFactory
import net.corda.v5.application.persistence.PagedQuery
import net.corda.v5.application.persistence.ParameterizedQuery
import net.corda.v5.base.annotations.Suspendable

/**
 * [NamedParameterizedQuery] is used to set and execute named queries.
 */
@Suppress("LongParameterList")
class NamedParameterizedQuery<R : Any>(
    private val externalEventExecutor: ExternalEventExecutor,
    private val resultSetFactory: ResultSetFactory,
    private val queryName: String,
    private var parameters: MutableMap<String, Any?>,
    private var limit: Int,
    private var offset: Int,
    private var expectedClass: Class<R>,
) : ParameterizedQuery<R> {

    override fun setLimit(limit: Int): ParameterizedQuery<R> {
        require (limit > 0) { "Limit cannot be negative or zero" }
        this.limit = limit
        return this
    }

    override fun setOffset(offset: Int): ParameterizedQuery<R> {
        require (offset >= 0) { "Offset cannot be negative" }
        this.offset = offset
        return this
    }

    override fun setParameter(name: String, value: Any?): ParameterizedQuery<R> {
        parameters[name] = value
        return this
    }

    override fun setParameters(parameters: Map<String, Any>): ParameterizedQuery<R> {
        this.parameters = parameters.toMutableMap()
        return this
    }

    @Suspendable
    override fun execute(): PagedQuery.ResultSet<R> {
        val resultSet = resultSetFactory.create(
            parameters,
            limit,
            offset,
            expectedClass
        ) @Suspendable { serializedParameters, offset ->
            wrapWithPersistenceException {
                externalEventExecutor.execute(
                    NamedQueryExternalEventFactory::class.java,
                    NamedQueryParameters(queryName, serializedParameters.toByteArrays(), offset, limit)
                )
            }
        }
        resultSet.next()
        return resultSet
    }
}
