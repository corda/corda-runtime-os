package net.corda.flow.application.persistence.query

import net.corda.flow.application.persistence.external.events.FindAllExternalEventFactory
import net.corda.flow.application.persistence.external.events.FindAllParameters
import net.corda.flow.application.persistence.wrapWithPersistenceException
import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.flow.persistence.query.ResultSetFactory
import net.corda.v5.application.persistence.PagedQuery
import net.corda.v5.base.annotations.Suspendable

/**
 * Object used to execute paged find queries against a database.
 */
class PagedFindQuery<R : Any>(
    private val externalEventExecutor: ExternalEventExecutor,
    private val resultSetFactory: ResultSetFactory,
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
        val resultSet = resultSetFactory.create(
            emptyMap(),
            limit,
            offset,
            entityClass
        ) @Suspendable { _, offset ->
            wrapWithPersistenceException {
                externalEventExecutor.execute(
                    FindAllExternalEventFactory::class.java,
                    FindAllParameters(entityClass, offset, limit)
                )
            }
        }
        resultSet.next()
        return resultSet
    }
}