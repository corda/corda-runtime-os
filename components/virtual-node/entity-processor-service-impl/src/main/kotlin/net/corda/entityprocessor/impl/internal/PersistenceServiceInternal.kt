package net.corda.entityprocessor.impl.internal

import net.corda.data.virtualnode.DeleteEntity
import net.corda.data.virtualnode.FindEntity
import net.corda.data.virtualnode.MergeEntity
import net.corda.data.virtualnode.PersistEntity
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.serialization.SerializedBytes
import net.corda.virtualnode.HoldingIdentity
import javax.persistence.EntityManager

fun EntitySandboxService.getClass(holdingIdentity: HoldingIdentity, fullyQualifiedClassName: String) =
    this.get(holdingIdentity).sandboxGroup.loadClassFromMainBundles(fullyQualifiedClassName)

/**
 * This the final set of calls that originated from the flow worker [PersistenceService], i.e.
 *
 * Flow worker:
 *
 *    persistenceService.persist(someEntity)
 *
 * Kafka -> EntityRequest
 *
 * Here:
 *
 *    persistenceServiceInternal.persist(ss, emf, someEntityWrappedBytes)
 *    publisher.publish(FLOW_WORKER_EVENT_TOPIC, EntityResponse(...))
 *
 * Kafka -> EntityResponse
 *
 * Flow worker:
 *
 *    // resumes...
 * */
class PersistenceServiceInternal(private val entitySandboxService: EntitySandboxService) {
    // TODO - these want to also handle exceptions and/or possible return EntityResponse here instead of bytes.


    fun persist(
        serializationService: SerializationService,
        entityManager: EntityManager,
        payload: PersistEntity
    ): SerializedBytes<Any>? {
        val entity = serializationService.deserialize(payload.entity.array(), Any::class.java)
        entityManager.persist(entity)
        return null
    }

    fun find(
        serializationService: SerializationService,
        entityManager: EntityManager,
        payload: FindEntity,
        holdingIdentity: HoldingIdentity
    ): SerializedBytes<Any>? {
        val primaryKey = serializationService.deserialize(payload.primaryKey.array(), Any::class.java)
        val clazz = entitySandboxService.getClass(holdingIdentity, payload.entityClassName)
        val entity = entityManager.find(clazz, primaryKey)
        return if (entity == null) {
            null
        } else {
            serializationService.serialize(entity)
        }
    }

    fun merge(
        serializationService: SerializationService,
        entityManager: EntityManager,
        payload: MergeEntity
    ): SerializedBytes<Any> {
        val entity = serializationService.deserialize(payload.entity.array(), Any::class.java)
        return serializationService.serialize(entityManager.merge(entity))
    }

    fun remove(
        serializationService: SerializationService,
        entityManager: EntityManager,
        payload: DeleteEntity
    ): SerializedBytes<Any>? {
        val entity = serializationService.deserialize(payload.entity.array(), Any::class.java)
        // NOTE: JPA expects the entity to be managed before removing, hence the merge.
        entityManager.remove(entityManager.merge(entity))
        return null
    }
}
