package net.corda.entityprocessor.impl.internal

import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.persistence.DeleteEntities
import net.corda.data.persistence.DeleteEntitiesById
import net.corda.data.persistence.EntityResponse
import net.corda.data.persistence.FindAll
import net.corda.data.persistence.FindEntities
import net.corda.data.persistence.FindWithNamedQuery
import net.corda.data.persistence.MergeEntities
import net.corda.data.persistence.PersistEntities
import net.corda.persistence.common.exceptions.InvalidPaginationException
import net.corda.utilities.serialization.deserialize
import net.corda.v5.application.serialization.SerializationService
import net.corda.virtualnode.HoldingIdentity
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import javax.persistence.EntityManager
import javax.persistence.Query
import javax.persistence.criteria.Selection


/**
 * This the final set of calls that originated from the flow worker [net.corda.v5.application.persistence.PersistenceService], i.e.
 *
 * Flow worker:
 *
 *    persistenceService.persist(someEntity)
 *
 * Kafka -> EntityRequest
 *
 * This (pseudo) code:
 *
 *    val entityResponse
 *      = persistenceServiceInternal.persist(ss, emf, someEntityWrappedBytes)
 *    publisher.publish(FLOW_WORKER_EVENT_TOPIC, entityResponse)
 *
 * Kafka -> EntityResponse
 *
 * Flow worker:
 *
 *    // resumes processing
 *
 * If the [EntityResponse] contains an exception, the flow-worker is expected to treat that
 * as an error and handle it appropriately (such as retrying).
 *
 * @param classProvider a lambda that returns the class _type_ for the given fully qualified name and
 * a given holding identity.  The supplied lambda should look up the class type in a context appropriate
 * to the given [HoldingIdentity]
 * */
class PersistenceServiceInternal(
    private val classProvider: (fullyQualifiedClassName: String) -> Class<*>) {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private fun SerializationService.toBytes(obj: Any) = ByteBuffer.wrap(serialize(obj).bytes)


    fun persist(
        serializationService: SerializationService,
        entityManager: EntityManager,
        payload: PersistEntities
    ): EntityResponse {
        payload.entities.map { entityManager.persist(serializationService.deserialize(it.array(), Any::class.java)) }
        return EntityResponse(emptyList(), KeyValuePairList(emptyList()), null)
    }

    fun find(
        serializationService: SerializationService,
        entityManager: EntityManager,
        payload: FindEntities
    ): EntityResponse {
        val clazz = classProvider(payload.entityClassName)
        val results = payload.ids.mapNotNull { serializedId ->
            val id = serializationService.deserialize(serializedId.array(), Any::class.java)
            entityManager.find(clazz, id)?.let { entity -> serializationService.toBytes(entity) }
        }
        return EntityResponse(results, KeyValuePairList(emptyList()), null)
    }

    fun merge(
        serializationService: SerializationService,
        entityManager: EntityManager,
        payload: MergeEntities
    ): EntityResponse {
        val results = payload.entities.map {
            val entity = serializationService.deserialize(it.array(), Any::class.java)
            entityManager.merge(entity)
        }
        return EntityResponse(results.map { serializationService.toBytes(it) }, KeyValuePairList(emptyList()), null)
    }

    fun deleteEntities(
        serializationService: SerializationService,
        entityManager: EntityManager,
        payload: DeleteEntities
    ): EntityResponse {
        // NOTE: JPA expects the entity to be managed before removing, hence the merge.
        payload.entities.map {
            val entity = serializationService.deserialize(it.array(), Any::class.java)
            entityManager.remove(entityManager.merge(entity))
        }
        return EntityResponse(emptyList(), KeyValuePairList(emptyList()), null)
    }

    /**
     * Remove by id / primary key.
     */
    fun deleteEntitiesByIds(
        serializationService: SerializationService,
        entityManager: EntityManager,
        payload: DeleteEntitiesById
    ): EntityResponse {
        payload.ids.map {
            val id = serializationService.deserialize(it.array(), Any::class.java)
            val clazz = classProvider(payload.entityClassName)
            logger.info("Deleting $id")
            val entity = entityManager.find(clazz, id)
            if (entity != null) {
                // NOTE: JPA expects the entity to be managed before removing, hence the merge.
                entityManager.remove(entityManager.merge(entity))
            } else {
                logger.debug("Entity not found for deletion: ${payload.entityClassName} and id: $id")
            }
        }
        return EntityResponse(emptyList(), KeyValuePairList(emptyList()), null)
    }

    /**
     * Find all entities of the class given in [FindAll]
     */
    fun findAll(
        serializationService: SerializationService,
        entityManager: EntityManager,
        payload: FindAll
    ): EntityResponse {
        val clazz = classProvider(payload.entityClassName)
        val cb = entityManager.criteriaBuilder
        val cq = cb.createQuery(clazz)
        val rootEntity = cq.from(clazz)

        // https://kotlinlang.org/docs/generics.html#variancecv
        @Suppress("Unchecked_cast")
        val all = cq.select(rootEntity as Selection<out Nothing>?)

        val typedQuery = entityManager.createQuery(all)
        return findWithQuery(serializationService, typedQuery, payload.offset, payload.limit)
    }

    /*
     * Find all entities that match a named query
     */
    fun findWithNamedQuery(
        serializationService: SerializationService,
        entityManager: EntityManager,
        payload: FindWithNamedQuery
    ): EntityResponse {
        val query = entityManager.createNamedQuery(payload.queryName)

        payload.parameters.forEach { rec ->
            query.setParameter(
                rec.key,
                rec.value?.let { serializationService.deserialize(it.array()) }
            )
        }
        return findWithQuery(serializationService, query, payload.offset, payload.limit)
    }


    /*
    * Find all entities that match a query, with pagination
    */
    private fun findWithQuery(
        serializationService: SerializationService,
        query: Query,
        offset: Int = 0,
        limit: Int = Int.MAX_VALUE,
    ): EntityResponse {
        if (offset < 0) throw InvalidPaginationException("Invalid negative offset $offset")
        if (offset != 0) {
            query.firstResult = offset
        }
        if (limit < 0) throw InvalidPaginationException("Invalid negative limit $limit")
        if (limit != Int.MAX_VALUE) {
            query.maxResults = limit
        }

        val results = query.resultList
        val result = when (results ) {
            null -> emptyList()
            else -> results.filterNotNull().map { item -> serializationService.toBytes(item) }
        }
        return EntityResponse(result, KeyValuePairList(listOf(KeyValuePair("numberOfRowsFromQuery", results.size.toString()))), null)
    }
}
