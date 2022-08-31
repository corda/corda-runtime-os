package net.corda.flow.application.persistence

import java.nio.ByteBuffer
import net.corda.flow.application.persistence.external.events.FindExternalEventFactory
import net.corda.flow.application.persistence.external.events.FindParameters
import net.corda.flow.application.persistence.external.events.MergeExternalEventFactory
import net.corda.flow.application.persistence.external.events.MergeParameters
import net.corda.flow.application.persistence.external.events.PersistExternalEventFactory
import net.corda.flow.application.persistence.external.events.PersistParameters
import net.corda.flow.application.persistence.external.events.RemoveExternalEventFactory
import net.corda.flow.application.persistence.external.events.RemoveParameters
import net.corda.flow.application.persistence.query.PagedQueryFactory
import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.flow.fiber.FlowFiberSerializationService
import net.corda.v5.application.persistence.PagedQuery
import net.corda.v5.application.persistence.ParameterisedQuery
import net.corda.v5.application.persistence.PersistenceService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope

@Suppress("TooManyFunctions")
@Component(service = [PersistenceService::class, SingletonSerializeAsToken::class], scope = ServiceScope.PROTOTYPE)
class PersistenceServiceImpl @Activate constructor(
    @Reference(service = ExternalEventExecutor::class)
    private val externalEventExecutor: ExternalEventExecutor,
    @Reference(service = PagedQueryFactory::class)
    private val pagedQueryFactory: PagedQueryFactory,
    @Reference(service = FlowFiberSerializationService::class)
    private val flowFiberSerializationService: FlowFiberSerializationService
) : PersistenceService, SingletonSerializeAsToken {

    @Suspendable
    override fun <R : Any> find(entityClass: Class<R>, primaryKey: Any): R? {
        return wrapWithPersistenceException {
            externalEventExecutor.execute(
                FindExternalEventFactory::class.java,
                FindParameters(entityClass, serialize(primaryKey))
            )
        }.firstOrNull()?.let { flowFiberSerializationService.deserialize(it.array(), entityClass) }
    }

    @Suspendable
    override fun <R : Any> find(entityClass: Class<R>, primaryKeys: List<Any>): List<R> {
        TODO("Not yet implemented")
    }

    @Suspendable
    override fun <R : Any> findAll(entityClass: Class<R>): PagedQuery<R> {
        return pagedQueryFactory.createPagedFindQuery(entityClass)
    }


    @Suspendable
    override fun <R : Any> merge(entity: R): R? {
        return wrapWithPersistenceException {
            externalEventExecutor.execute(
                MergeExternalEventFactory::class.java,
                MergeParameters(serialize(entity))
            )
        }.firstOrNull()?.let { flowFiberSerializationService.deserialize(it.array(), entity::class.java) }
    }

    @Suspendable
    override fun <R : Any> merge(entities: List<R>): List<R> {
        TODO("Not yet implemented")
    }

    @Suspendable
    override fun persist(entity: Any) {
        wrapWithPersistenceException {
            externalEventExecutor.execute(
                PersistExternalEventFactory::class.java,
                PersistParameters(serialize(entity))
            )
        }
    }

    override fun persist(entities: List<Any>) {
        TODO("Not yet implemented")
    }

    @Suspendable
    override fun remove(entity: Any) {
        enforceNotPrimitive(entity::class.java)
        wrapWithPersistenceException {
            externalEventExecutor.execute(
                RemoveExternalEventFactory::class.java,
                RemoveParameters(serialize(entity))
            )
        }
    }

    @Suspendable
    override fun remove(entities: List<Any>) {
        TODO("Not yet implemented")
    }

    @Suspendable
    override fun <T : Any> query(
        queryName: String,
        entityClass: Class<T>
    ): ParameterisedQuery<T> {
        return pagedQueryFactory.createNamedParameterisedQuery(queryName, entityClass)
    }

    /**
     * Required to prevent class cast exceptions during AMQP serialization of primitive types.
     */
    private fun enforceNotPrimitive(type: Class<*>) {
        require(!type.isPrimitive) { "Cannot receive primitive type $type" }
    }

    private fun serialize(payload: Any): ByteBuffer {
        return ByteBuffer.wrap(flowFiberSerializationService.serialize(payload).bytes)
    }
}