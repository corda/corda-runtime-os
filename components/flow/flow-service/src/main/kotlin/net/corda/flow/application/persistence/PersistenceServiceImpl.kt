package net.corda.flow.application.persistence

import java.io.NotSerializableException
import java.util.UUID
import net.corda.flow.fiber.FlowFiber
import net.corda.flow.fiber.FlowFiberService
import net.corda.flow.fiber.FlowIORequest
import net.corda.v5.application.persistence.PersistenceQueryRequest
import net.corda.v5.application.persistence.PersistenceService
import net.corda.v5.application.persistence.query.NamedQueryFilter
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.stream.Cursor
import net.corda.v5.base.util.castIfPossible
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope

@Suppress("TooManyFunctions")
@Component(service = [PersistenceService::class, SingletonSerializeAsToken::class], scope = ServiceScope.PROTOTYPE)
class PersistenceServiceImpl @Activate constructor(
    @Reference(service = FlowFiberService::class)
    private val flowFiberService: FlowFiberService,
) : PersistenceService, SingletonSerializeAsToken {

    private companion object {
        val log = contextLogger()
    }

    private val fiber: FlowFiber get() = flowFiberService.getExecutingFiber()

    @Suspendable
    override fun <R : Any> find(entityClass: Class<R>, primaryKey: Any): R? {
        val request = FlowIORequest.Find(UUID.randomUUID().toString(), entityClass.canonicalName, serialize(primaryKey))
        log.debug { "Preparing to send Find query for class of type ${request.className} with id ${request.requestId} " }
        val received = fiber.suspend(request)
        return if (received != null) deserializeReceivedPayload(received.array(), entityClass) else null
    }

    @Suspendable
    override fun <R : Any> find(entityClass: Class<R>, primaryKeys: List<Any>): List<R> {
        TODO("Not yet implemented")
    }

    @Suspendable
    override fun <R : Any> merge(entity: R): R? {
        val entityClass = entity::class.java
        val request = FlowIORequest.Merge(UUID.randomUUID().toString(), serialize(entity))
        log.debug { "Preparing to send Merge query for class of type $entityClass with id ${request.requestId} " }
        val received = fiber.suspend(request)
        return if (received != null) deserializeReceivedPayload(received.array(), entityClass) else null
    }

    @Suspendable
    override fun <R : Any> merge(entities: List<R>): List<R> {
        TODO("Not yet implemented")
    }

    @Suspendable
    override fun persist(entity: Any) {
        val request = FlowIORequest.Persist(UUID.randomUUID().toString(), serialize(entity))
        log.debug { "Preparing to send Persist query for class of type ${entity::class.java} with id ${request.requestId} " }
        fiber.suspend(request)
    }

    @Suspendable
    override fun persist(entities: List<Any>) {
        TODO("Not yet implemented")
    }

    @Suspendable
    override fun <R> query(queryName: String, namedParameters: Map<String, Any>): Cursor<R> {
        TODO("Not yet implemented")
    }

    @Suspendable
    override fun <R> query(queryName: String, namedParameters: Map<String, Any>, postProcessorName: String): Cursor<R> {
        TODO("Not yet implemented")
    }

    @Suspendable
    override fun <R> query(queryName: String, namedParameters: Map<String, Any>, postFilter: NamedQueryFilter): Cursor<R> {
        TODO("Not yet implemented")
    }

    @Suspendable
    override fun <R> query(
        queryName: String,
        namedParameters: Map<String, Any>,
        postFilter: NamedQueryFilter,
        postProcessorName: String
    ): Cursor<R> {
        TODO("Not yet implemented")
    }

    @Suspendable
    override fun <R> query(persistenceQueryRequest: PersistenceQueryRequest): Cursor<R> {
        TODO("Not yet implemented")
    }

    @Suspendable
    override fun remove(entity: Any) {
        val entityClass = entity::class.java
        enforceNotPrimitive(entityClass)
        val request = FlowIORequest.Delete(UUID.randomUUID().toString(), serialize(entity))
        log.debug { "Preparing to send Delete query for class of type $entityClass with id ${request.requestId} " }
        fiber.suspend(request)
    }

    @Suspendable
    override fun remove(entities: List<Any>) {
        TODO("Not yet implemented")
    }


    /**
     * Required to prevent class cast exceptions during AMQP serialization of primitive types.
     */
    private fun enforceNotPrimitive(type: Class<*>) {
        require(!type.isPrimitive) { "Cannot receive primitive type $type" }
    }

    private fun serialize(payload: Any): ByteArray {
        return getSerializationService().serialize(payload).bytes
    }

    private fun <R : Any> deserializeReceivedPayload(received: ByteArray, receiveType: Class<R>): R {
        return try {
                val payload = getSerializationService().deserialize(received, receiveType)
                checkPayloadIs(payload, receiveType)
                payload
            } catch (e: NotSerializableException) {
                log.info("Received a payload but failed to deserialize it into a ${receiveType.name}", e)
                throw e
            }

    }

    /**
     * AMQP deserialization outputs an object whose type is solely based on the serialized content, therefore although the generic type is
     * specified, it can still be the wrong type. We check this type here, so that we can throw an accurate error instead of failing later
     * on when the object is used.
     */
    private fun <R : Any> checkPayloadIs(payload: Any, receiveType: Class<R>) {
        receiveType.castIfPossible(payload) ?: throw CordaRuntimeException(
            "Expecting to receive a ${receiveType.name} but received a ${payload.javaClass.name} instead, payload: ($payload)"
        )
    }

    private fun getSerializationService(): SerializationService {
        return fiber.getExecutionContext().run {
            sandboxGroupContext.amqpSerializer
        }
    }
}
