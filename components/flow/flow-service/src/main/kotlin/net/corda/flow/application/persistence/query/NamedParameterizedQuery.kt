package net.corda.flow.application.persistence.query

import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.v5.application.persistence.PagedQuery
import net.corda.v5.application.persistence.ParameterizedQuery
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable

/**
 * [NamedParameterizedQuery] is used to set and execute named queries.
 */
@Suppress("LongParameterList")
class NamedParameterizedQuery<R : Any>(
    private val externalEventExecutor: ExternalEventExecutor,
    private val serializationService: SerializationService,
    private val queryName: String,
    private var parameters: MutableMap<String, Any>,
    private var limit: Int,
    private var offset: Int,
    private var expectedClass: Class<R>,
) : ParameterizedQuery<R> {

    override fun setLimit(limit: Int): ParameterizedQuery<R> {
        require (limit >= 0) { "Limit cannot be negative" }
        this.limit = limit
        return this
    }

    override fun setOffset(offset: Int): ParameterizedQuery<R> {
        require (offset >= 0) { "Offset cannot be negative" }
        this.offset = offset
        return this
    }

    override fun setParameter(name: String, value: Any): ParameterizedQuery<R> {
        parameters[name] = value
        return this
    }

    override fun setParameters(parameters: Map<String, Any>): ParameterizedQuery<R> {
        this.parameters = parameters.toMutableMap()
        return this
    }

    @Suspendable
    override fun execute(): PagedQuery.ResultSet<R> {
        val resultSet = EntityResultSetImpl(
            queryName,
            externalEventExecutor,
            serializationService,
            parameters,
            limit,
            offset,
            expectedClass
        )
        resultSet.next()
        return resultSet
    }
}