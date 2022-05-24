package net.corda.entityprocessor.impl.internal

import net.corda.data.virtualnode.DeleteEntity
import net.corda.data.virtualnode.EntityResponse
import net.corda.data.virtualnode.EntityResponseSuccess
import net.corda.data.virtualnode.FindEntity
import net.corda.data.virtualnode.MergeEntity
import net.corda.data.virtualnode.PersistEntity
import net.corda.v5.application.serialization.SerializationService
import net.corda.virtualnode.HoldingIdentity
import java.nio.ByteBuffer
import java.time.Instant
import javax.persistence.EntityManager


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
    private val requestId: String
) {
    fun persist(
        serializationService: SerializationService,
        entityManager: EntityManager,
        payload: PersistEntity
    ): EntityResponse {
        val entity = serializationService.deserialize(payload.entity.array(), Any::class.java)
        entityManager.persist(entity)
        return EntityResponse(Instant.now(), requestId, EntityResponseSuccess())
    }

    fun find(
        serializationService: SerializationService,
        entityManager: EntityManager,
        payload: FindEntity,
        holdingIdentity: HoldingIdentity
    ): EntityResponse {
        val primaryKey = serializationService.deserialize(payload.primaryKey.array(), Any::class.java)
        val clazz = classProvider(holdingIdentity, payload.entityClassName)
        val innerMsg = when (val entity = entityManager.find(clazz, primaryKey)) {
            null -> EntityResponseSuccess()
            else -> EntityResponseSuccess(ByteBuffer.wrap(serializationService.serialize(entity).bytes))
        }
        return EntityResponse(Instant.now(), requestId, innerMsg)
    }

    fun merge(
        serializationService: SerializationService,
        entityManager: EntityManager,
        payload: MergeEntity
    ): EntityResponse {
        val entity = serializationService.deserialize(payload.entity.array(), Any::class.java)
        val innerMsg =
            EntityResponseSuccess(ByteBuffer.wrap(serializationService.serialize(entityManager.merge(entity)).bytes))
        return EntityResponse(Instant.now(), requestId, innerMsg)
    }

    fun remove(
        serializationService: SerializationService,
        entityManager: EntityManager,
        payload: DeleteEntity
    ): EntityResponse {
        val entity = serializationService.deserialize(payload.entity.array(), Any::class.java)
        // NOTE: JPA expects the entity to be managed before removing, hence the merge.
        entityManager.remove(entityManager.merge(entity))
        return EntityResponse(Instant.now(), requestId, EntityResponseSuccess())
    }
}
