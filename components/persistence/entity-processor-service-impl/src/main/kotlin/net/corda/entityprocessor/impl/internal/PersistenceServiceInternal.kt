package net.corda.entityprocessor.impl.internal

import net.corda.data.persistence.DeleteEntities
import net.corda.data.persistence.DeleteEntitiesById
import net.corda.data.persistence.EntityResponse
import net.corda.data.persistence.FindAll
import net.corda.data.persistence.FindEntities
import net.corda.data.persistence.FindWithNamedQuery
import net.corda.data.persistence.MergeEntities
import net.corda.data.persistence.PersistEntities
import net.corda.persistence.common.exceptions.InvalidPaginationException
import net.corda.persistence.common.exceptions.NullParameterException
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.application.serialization.deserialize
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
    private val classProvider: (holdingIdentity: HoldingIdentity, fullyQualifiedClassName: String) -> Class<*>,
    private val payloadCheck: (bytes: ByteBuffer) -> ByteBuffer
) {
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
        return EntityResponse(emptyList())
    }

    fun find(
        serializationService: SerializationService,
        entityManager: EntityManager,
        payload: FindEntities,
        holdingIdentity: HoldingIdentity
    ): EntityResponse {
        val clazz = classProvider(holdingIdentity, payload.entityClassName)
        val results = payload.ids.mapNotNull { serializedId ->
            val id = serializationService.deserialize(serializedId.array(), Any::class.java)
            entityManager.find(clazz, id)?.let { entity -> payloadCheck(serializationService.toBytes(entity)) }
        }
        return EntityResponse(results)
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
        return EntityResponse(results.map { payloadCheck(serializationService.toBytes(it)) })
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
        return EntityResponse(emptyList())
    }

    /**
     * Remove by id / primary key.
     */
    fun deleteEntitiesByIds(
        serializationService: SerializationService,
        entityManager: EntityManager,
        payload: DeleteEntitiesById,
        holdingIdentity: HoldingIdentity
    ): EntityResponse {
        payload.ids.map {
            val id = serializationService.deserialize(it.array(), Any::class.java)
            val clazz = classProvider(holdingIdentity, payload.entityClassName)
            logger.info("Deleting $id")
            val entity = entityManager.find(clazz, id)
            if (entity != null) {
                // NOTE: JPA expects the entity to be managed before removing, hence the merge.
                entityManager.remove(entityManager.merge(entity))
            } else {
                logger.debug("Entity not found for deletion: ${payload.entityClassName} and id: $id")
            }
        }
        return EntityResponse(emptyList())
    }

    /**
     * Find all entities of the class given in [FindAll]
     */
    fun findAll(
        serializationService: SerializationService,
        entityManager: EntityManager,
        payload: FindAll,
        holdingIdentity: HoldingIdentity
    ): EntityResponse {
        val clazz = classProvider(holdingIdentity, payload.entityClassName)
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
        // We do not support null values for parameters. This is because:
        // 1. SQL equality to a null parameter to the query mostly doesn't do what the user might expect, in that
        //    it won't match since you typically have to use "IS NULL" rather than "foo=:param" where param is null.
        // 2. Hibernate does not translate = NULL to IS NULL.
        // 3. Nulls introduce complexity into the serialization - you have to have a way of saying "null of type X".
        //    Our Kotlin serialization code uses type Any which is non-nullable for serialization, so
        //    our code should never put null into the value.
        //
        //    We do not want to assume the Avro generated code and/or some caller producing messages by some other
        //    means couldn't somehow get a null in a parameter value, so we'll throw an error, which we expect to get
        //    caught and send back an error response message in the calling code.
        val nullParamNames = payload.parameters.filter { it.value == null }.map { it.key }
        if (nullParamNames.isNotEmpty()) {
            val msg = "Null value found for parameters ${nullParamNames.joinToString(", ")}"
            logger.error(msg)
            throw NullParameterException(msg)
        }
        payload.parameters.filter { it.value != null}.forEach { rec ->
            val bytes = rec.value.array()
            query.setParameter(rec.key, serializationService.deserialize(bytes))
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

        val result = when (val results = query.resultList) {
            null -> emptyList()
            else -> results.filterNotNull().map { item -> payloadCheck(serializationService.toBytes(item)) }
        }
        return EntityResponse(result)
    }
}
