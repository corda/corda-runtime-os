package net.corda.flow.application.persistence.query

import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.v5.application.persistence.PagedQuery
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable

/**
 * Object used to execute paged find queries against a database.
 */
class PagedFindQuery<R : Any>(
    private val externalEventExecutor: ExternalEventExecutor,
    private val serializationService: SerializationService,
    private val entityClass: Class<R>,
    private var limit: Int,
    private var offset: Int,
) : PagedQuery<R> {

    override fun setLimit(limit: Int): PagedQuery<R> {
        require (limit >= 0) { "Limit cannot be negative" }
        this.limit = limit
        return this
    }

    override fun setOffset(offset: Int): PagedQuery<R> {
        require (offset >= 0) { "Offset cannot be negative" }
        this.offset = offset
        return this
    }

    @Suspendable
    override fun execute(): PagedQuery.ResultSet<R> {
        val resultSet = FindAllResultSetImpl(
            externalEventExecutor,
            serializationService,
            limit,
            offset,
            entityClass
        )
        resultSet.next()
        return resultSet
    }
}