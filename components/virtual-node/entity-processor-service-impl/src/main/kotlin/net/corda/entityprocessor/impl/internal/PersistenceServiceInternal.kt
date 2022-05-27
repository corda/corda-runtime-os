package net.corda.entityprocessor.impl.internal

import net.corda.data.persistence.DeleteEntityById
import net.corda.data.persistence.DeleteEntity
import net.corda.data.persistence.EntityResponse
import net.corda.data.persistence.EntityResponseSuccess
import net.corda.data.persistence.FindAll
import net.corda.data.persistence.FindEntity
import net.corda.data.persistence.MergeEntity
import net.corda.data.persistence.PersistEntity
import net.corda.utilities.time.Clock
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.util.contextLogger
import net.corda.virtualnode.HoldingIdentity
import java.nio.ByteBuffer
import javax.persistence.EntityManager
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
 * @param requestId the request id for this action.
 * */
class PersistenceServiceInternal(
    private val classProvider: (holdingIdentity: HoldingIdentity, fullyQualifiedClassName: String) -> Class<*>,
    private val requestId: String,
    private val clock: Clock,
    private val payloadCheck: (bytes: ByteBuffer) -> ByteBuffer
) {
    companion object {
        // Max bytes we will return (for findAll) in a single Kafka packet / message
        private const val MAX_BYTES = 6 * 1024 * 1024
        private val logger = contextLogger()
    }

    private fun SerializationService.toBytes(obj: Any) = ByteBuffer.wrap(serialize(obj).bytes)


    fun persist(
        serializationService: SerializationService,
        entityManager: EntityManager,
        payload: PersistEntity
    ): EntityResponse {
        val entity = serializationService.deserialize(payload.entity.array(), Any::class.java)
        entityManager.persist(entity)
        return EntityResponse(clock.instant(), requestId, EntityResponseSuccess())
    }

    fun find(
        serializationService: SerializationService,
        entityManager: EntityManager,
        payload: FindEntity,
        holdingIdentity: HoldingIdentity
    ): EntityResponse {
        val id = serializationService.deserialize(payload.id.array(), Any::class.java)
        val clazz = classProvider(holdingIdentity, payload.entityClassName)
        val innerMsg = when (val entity = entityManager.find(clazz, id)) {
            null -> EntityResponseSuccess()
            else -> EntityResponseSuccess(payloadCheck(serializationService.toBytes(entity)))
        }
        return EntityResponse(clock.instant(), requestId, innerMsg)
    }

    fun merge(
        serializationService: SerializationService,
        entityManager: EntityManager,
        payload: MergeEntity
    ): EntityResponse {
        val entity = serializationService.deserialize(payload.entity.array(), Any::class.java)
        val innerMsg = EntityResponseSuccess(payloadCheck(serializationService.toBytes(entityManager.merge(entity))))
        return EntityResponse(clock.instant(), requestId, innerMsg)
    }

    fun remove(
        serializationService: SerializationService,
        entityManager: EntityManager,
        payload: DeleteEntity
    ): EntityResponse {
        val entity = serializationService.deserialize(payload.entity.array(), Any::class.java)
        // NOTE: JPA expects the entity to be managed before removing, hence the merge.

        entityManager.remove(entityManager.merge(entity))
        return EntityResponse(clock.instant(), requestId, EntityResponseSuccess())
    }

    /**
     * Remove by id / primary key.
     */
    fun removeById(
        serializationService: SerializationService,
        entityManager: EntityManager,
        payload: DeleteEntityById,
        holdingIdentity: HoldingIdentity
    ): EntityResponse {
        val id = serializationService.deserialize(payload.id.array(), Any::class.java)
        val clazz = classProvider(holdingIdentity, payload.entityClassName)

        val entity = entityManager.find(clazz, id)
        if (entity != null) {
            // NOTE: JPA expects the entity to be managed before removing, hence the merge.
            entityManager.remove(entityManager.merge(entity))
        } else {
            logger.debug("Entity not found for deletion:  ${payload.entityClassName} and id: $id")
        }

        return EntityResponse(clock.instant(), requestId, EntityResponseSuccess())
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

        // https://kotlinlang.org/docs/generics.html#variance
        @Suppress("Unchecked_cast")
        val all = cq.select(rootEntity as Selection<out Nothing>?)

        val typedQuery = entityManager.createQuery(all)
        val innerMsg = when (val results = typedQuery.resultList) {
            null -> EntityResponseSuccess()
            else -> EntityResponseSuccess(payloadCheck(serializationService.toBytes(results)))
        }
        return EntityResponse(clock.instant(), requestId, innerMsg)
    }
}
