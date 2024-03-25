package net.corda.flow.application.persistence

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
    companion object {
        // We should ensure this aligns with the documentation in [PersistenceService]
        private const val MAX_DEDUPLICATION_ID_LENGTH = 128
    }

    @Suspendable
    override fun <R : Any> find(entityClass: Class<R>, primaryKey: Any): R? {
        return wrapWithPersistenceException {
            externalEventExecutor.execute(
                FindExternalEventFactory::class.java,
                FindParameters(entityClass, listOf(serialize(primaryKey)))
            )
        }.firstOrNull()?.let { serializationService.deserialize(it.array(), entityClass) }
    }

    @Suspendable
    override fun <R : Any> find(entityClass: Class<R>, primaryKeys: List<*>): List<R> {
        return wrapWithPersistenceException {
            externalEventExecutor.execute(
                FindExternalEventFactory::class.java,
                FindParameters(entityClass, primaryKeys.filterNotNull().map(::serialize))
            )
        }.map { serializationService.deserialize(it.array(), entityClass) }
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
                MergeParameters(listOf(serialize(entity)))
            )
        }.firstOrNull()?.let { serializationService.deserialize(it.array(), entity::class.java) }
    }

    @Suspendable
    override fun <R : Any> merge(entities: List<R>): List<R> {
        return if (entities.isNotEmpty()) {
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
    override fun persist(deduplicationId: String, entity: Any) {
        validateDeduplicationId(deduplicationId)

        wrapWithPersistenceException {
            externalEventExecutor.execute(
                PersistExternalEventFactory::class.java,
                PersistParameters(deduplicationId, listOf(serialize(entity)))
            )
        }
    }

    @Suspendable
    override fun persist(deduplicationId: String, entities: List<*>) {
        validateDeduplicationId(deduplicationId)

        if (entities.isNotEmpty()) {
            wrapWithPersistenceException {
                externalEventExecutor.execute(
                    PersistExternalEventFactory::class.java,
                    PersistParameters(deduplicationId, entities.filterNotNull().map(::serialize))
                )
            }
        }
    }

    @Suspendable
    override fun remove(entity: Any) {
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
        return pagedQueryFactory.createNamedParameterizedQuery(queryName, entityClass)
    }

    private fun serialize(payload: Any): ByteArray {
        return serializationService.serialize(payload).bytes
    }

    private fun validateDeduplicationId(deduplicationId: String) {
        if (deduplicationId.isEmpty() || deduplicationId.length > MAX_DEDUPLICATION_ID_LENGTH) {
            throw IllegalArgumentException(
                "deduplicationId must not be empty and must not exceed $MAX_DEDUPLICATION_ID_LENGTH characters. " +
                "Provided length: ${deduplicationId.length}."
            )
        }
    }
}
