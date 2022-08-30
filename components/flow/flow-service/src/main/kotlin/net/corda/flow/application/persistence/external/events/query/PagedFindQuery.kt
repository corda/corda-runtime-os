package net.corda.flow.application.persistence.external.events.query

import co.paralleluniverse.fibers.Suspendable
import net.corda.flow.application.persistence.external.events.FindAllExternalEventFactory
import net.corda.flow.application.persistence.external.events.FindAllParameters
import net.corda.flow.application.persistence.wrapWithPersistenceException
import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.flow.fiber.FlowFiberSerializationService
import net.corda.v5.application.persistence.PagedQuery

/**
 * Object used to execute paged find queries against a database.
 */
class PagedFindQuery<R : Any>(
    private val externalEventExecutor: ExternalEventExecutor,
    private val flowFiberSerializationService: FlowFiberSerializationService,
    private val entityClass: Class<R>,
    private var offset: Int,
    private var limit: Int,
) : PagedQuery<R> {

    @Suspendable
    override fun setLimit(limit: Int): PagedQuery<R> {
        this.limit = limit
        return this
    }

    @Suspendable
    override fun setOffset(offset: Int): PagedQuery<R> {
        this.offset = offset
        return this
    }

    @Suspendable
    override fun execute(): List<R> {
        val deserialized = wrapWithPersistenceException {
            externalEventExecutor.execute(
                FindAllExternalEventFactory::class.java,
                FindAllParameters(entityClass, offset, limit)
            )
        }.mapNotNull { flowFiberSerializationService.deserializePayload(it.array(), entityClass) }

        return deserialized
    }
}