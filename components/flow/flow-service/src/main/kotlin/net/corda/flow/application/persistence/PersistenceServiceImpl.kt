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
import net.corda.sandbox.type.UsedByFlow
import net.corda.v5.application.persistence.PagedQuery
import net.corda.v5.application.persistence.ParameterizedQuery
import net.corda.v5.application.persistence.PersistenceService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE

@Suppress("TooManyFunctions")
@Component(service = [ PersistenceService::class, UsedByFlow::class ], scope = PROTOTYPE)
class PersistenceServiceImpl @Activate constructor(
    @Reference(service = ExternalEventExecutor::class)
    private val externalEventExecutor: ExternalEventExecutor,
    @Reference(service = PagedQueryFactory::class)
    private val pagedQueryFactory: PagedQueryFactory,
    @Reference(service = SerializationService::class)
    private val serializationService: SerializationService
) : PersistenceService, UsedByFlow, SingletonSerializeAsToken {

    @Suspendable
    override fun <R : Any> find(entityClass: Class<R>, primaryKey: Any): R? {
        requireBoxedType(entityClass)
        return wrapWithPersistenceException {
            externalEventExecutor.execute(
                FindExternalEventFactory::class.java,
                FindParameters(entityClass, listOf(serialize(primaryKey)))
            )
        }.firstOrNull()?.let { serializationService.deserialize(it.array(), entityClass) }
    }

    @Suspendable
    override fun <R : Any> find(entityClass: Class<R>, primaryKeys: List<*>): List<R> {
        requireBoxedType(entityClass)
        return wrapWithPersistenceException {
            externalEventExecutor.execute(
                FindExternalEventFactory::class.java,
                FindParameters(entityClass, primaryKeys.filterNotNull().map(::serialize))
            )
        }.map { serializationService.deserialize(it.array(), entityClass) }
    }

    @Suspendable
    override fun <R : Any> findAll(entityClass: Class<R>): PagedQuery<R> {
        requireBoxedType(entityClass)
        return pagedQueryFactory.createPagedFindQuery(entityClass)
    }

    @Suspendable
    override fun <R : Any> merge(entity: R): R? {
        requireBoxedType(entity.javaClass)
        return wrapWithPersistenceException {
            externalEventExecutor.execute(
                MergeExternalEventFactory::class.java,
                MergeParameters(listOf(serialize(entity)))
            )
        }.firstOrNull()?.let { serializationService.deserialize(it.array(), entity::class.java) }
    }

    @Suspendable
    override fun <R : Any> merge(entities: List<R>): List<R> {
        return if (entities.isNotEmpty()) {
            requireBoxedType(entities)
            val mergedEntities = wrapWithPersistenceException {
                externalEventExecutor.execute(
                    MergeExternalEventFactory::class.java,
                    MergeParameters(entities.map { serialize(it) })
                )
            }
            // Zips the merged entities with the [entities] passed into [merge] so that [mergedEntities] can be
            // deserialized into the correct types. This assumes that the order of [mergedEntities] is the same as
            // [entities].
            entities.zip(mergedEntities).map { (entity, mergedEntity) ->
                serializationService.deserialize(mergedEntity.array(), entity::class.java)
            }
        } else {
            emptyList()
        }
    }

    @Suspendable
    override fun persist(entity: Any) {
        requireBoxedType(entity.javaClass)
        wrapWithPersistenceException {
            externalEventExecutor.execute(
                PersistExternalEventFactory::class.java,
                PersistParameters(listOf(serialize(entity)))
            )
        }
    }

    @Suspendable
    override fun persist(entities: List<*>) {
        if (entities.isNotEmpty()) {
            requireBoxedType(entities)
            wrapWithPersistenceException {
                externalEventExecutor.execute(
                    PersistExternalEventFactory::class.java,
                    PersistParameters(entities.filterNotNull().map(::serialize))
                )
            }
        }
    }

    @Suspendable
    override fun remove(entity: Any) {
        requireBoxedType(entity.javaClass)
        wrapWithPersistenceException {
            externalEventExecutor.execute(
                RemoveExternalEventFactory::class.java,
                RemoveParameters(listOf(serialize(entity)))
            )
        }
    }

    @Suspendable
    override fun remove(entities: List<*>) {
        if (entities.isNotEmpty()) {
            requireBoxedType(entities)
            wrapWithPersistenceException {
                externalEventExecutor.execute(
                    RemoveExternalEventFactory::class.java,
                    RemoveParameters(entities.filterNotNull().map(::serialize))
                )
            }
        }
    }

    @Suspendable
    override fun <T : Any> query(
        queryName: String,
        entityClass: Class<T>
    ): ParameterizedQuery<T> {
        requireBoxedType(entityClass)
        return pagedQueryFactory.createNamedParameterizedQuery(queryName, entityClass)
    }

    /**
     * Required to prevent class cast exceptions during AMQP serialization of primitive types.
     */
    private fun requireBoxedType(type: Class<*>) {
        require(!type.isPrimitive) { "Cannot perform persistence operation on primitive type ${type.name}" }
    }

    private fun requireBoxedType(objects: List<*>) {
        for (obj in objects) {
            if (obj != null) {
                requireBoxedType(obj.javaClass)
            }
        }
    }

    private fun serialize(payload: Any): ByteBuffer {
        return ByteBuffer.wrap(serializationService.serialize(payload).bytes)
    }
}