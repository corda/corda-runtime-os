package net.corda.flow.application.persistence

import net.corda.data.persistence.DeleteEntity
import net.corda.data.persistence.EntityRequest
import net.corda.data.persistence.FindAll
import net.corda.data.persistence.FindEntity
import net.corda.data.persistence.MergeEntity
import net.corda.data.persistence.PersistEntity
import net.corda.flow.fiber.FlowFiber
import net.corda.flow.fiber.FlowFiberService
import net.corda.v5.application.persistence.PagedQuery
import net.corda.v5.application.persistence.ParameterisedQuery
import net.corda.flow.pipeline.handlers.events.ExternalEventRequest
import net.corda.schema.Schemas
import net.corda.v5.application.persistence.PersistenceService
import net.corda.v5.application.persistence.query.NamedQueryFilter
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.castIfPossible
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.v5.persistence.CordaPersistenceException
import net.corda.v5.serialization.SingletonSerializeAsToken
import net.corda.virtualnode.toAvro
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope
import java.io.NotSerializableException
import java.nio.ByteBuffer

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
        return suspend(FindEntity(entityClass.canonicalName, serialize(primaryKey))) {
            "Preparing to send Find query for class of type ${entityClass.canonicalName} with id $it"
        }?.let { deserializeReceivedPayload(it, entityClass) }
    }

    @Suspendable
    override fun <R : Any> find(entityClass: Class<R>, primaryKeys: List<Any>): List<R> {
        TODO("Not yet implemented")
    }

    @Suspendable
    override fun <R : Any> findAll(entityClass: Class<R>): PagedQuery<R> {
        // TODO - this probably want to be extracted for testing, for example, when implementing paging.
        return object : PagedQuery<R> {
            @Suspendable
            override fun execute(): List<R> {
                val deserialized = suspend(FindAll(entityClass.canonicalName)) {
                    "Preparing to send FindAll query for class of type ${entityClass.canonicalName} with id $it"
                }?.let { deserializeReceivedPayload(it, List::class.java) }

                if (deserialized != null) {
                    @Suppress("Unchecked_cast")
                    return deserialized as List<R>
                }
                return emptyList()
            }

            override fun setLimit(limit: Int): PagedQuery<R> {
                TODO("Not yet implemented")
            }

            override fun setOffset(offset: Int): PagedQuery<R> {
                TODO("Not yet implemented")
            }
    }

    @Suspendable
    override fun <R : Any> merge(entity: R): R? {
        return suspend(MergeEntity(serialize(entity))){
            "Preparing to send Merge query for class of type ${entity::class.java} with id $it"
        }?.let { deserializeReceivedPayload(it, entity::class.java) }
    }

    @Suspendable
    override fun <R : Any> merge(entities: List<R>): List<R> {
        TODO("Not yet implemented")
    }

    @Suspendable
    override fun persist(entity: Any) {
        suspend(PersistEntity(serialize(entity))) {
            "Preparing to send Persist query for class of type ${entity::class.java} with id $it"
        }
    }

    override fun persist(entities: List<Any>) {
        TODO("Not yet implemented")
    }

    @Suspendable
    override fun remove(entity: Any) {
        enforceNotPrimitive(entity::class.java)
        suspend(DeleteEntity(serialize(entity))) { "Preparing to send Delete query for class of type ${entity::class.java} with id $it" }
    }

    @Suspendable
    override fun remove(entities: List<Any>) {
        TODO("Not yet implemented")
    }

    @Suspendable
    override fun <T : Any> query(
        queryName: String,
        entityClass: Class<T>): ParameterisedQuery<T> {
        TODO("Not yet implemented")
    }

    @Suspendable
    private fun suspend(request: Any, debugLog: (requestId: String) -> String): ByteArray? {
        val holdingIdentity = flowFiberService.getExecutingFiber().getExecutionContext().holdingIdentity.toAvro()
        return try {
            flowFiberService.getExecutingFiber().suspend(
                ExternalEventRequest {
                    log.debug { debugLog(it) }
                    ExternalEventRequest.EventRecord(
                        Schemas.VirtualNode.ENTITY_PROCESSOR,
                        EntityRequest(holdingIdentity, request)
                    )
                }
            ).data
        } catch (e: CordaRuntimeException) {
            throw CordaPersistenceException(e.message ?: "Exception occurred when executing persistence operation")
        }
    }

    /**
     * Required to prevent class cast exceptions during AMQP serialization of primitive types.
     */
    private fun enforceNotPrimitive(type: Class<*>) {
        require(!type.isPrimitive) { "Cannot receive primitive type $type" }
    }

    private fun serialize(payload: Any): ByteBuffer {
        return ByteBuffer.wrap(getSerializationService().serialize(payload).bytes)
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
