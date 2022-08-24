package net.corda.flow.application.persistence

import java.io.NotSerializableException
import java.nio.ByteBuffer
import net.corda.flow.application.persistence.external.events.FindAllExternalEventFactory
import net.corda.flow.application.persistence.external.events.FindAllParameters
import net.corda.flow.application.persistence.external.events.FindExternalEventFactory
import net.corda.flow.application.persistence.external.events.FindParameters
import net.corda.flow.application.persistence.external.events.MergeExternalEventFactory
import net.corda.flow.application.persistence.external.events.MergeParameters
import net.corda.flow.application.persistence.external.events.PersistExternalEventFactory
import net.corda.flow.application.persistence.external.events.PersistParameters
import net.corda.flow.application.persistence.external.events.RemoveExternalEventFactory
import net.corda.flow.application.persistence.external.events.RemoveParameters
import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.flow.fiber.FlowFiber
import net.corda.flow.fiber.FlowFiberService
import net.corda.v5.application.persistence.CordaPersistenceException
import net.corda.v5.application.persistence.PagedQuery
import net.corda.v5.application.persistence.ParameterisedQuery
import net.corda.v5.application.persistence.PersistenceService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.castIfPossible
import net.corda.v5.base.util.contextLogger
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
    @Reference(service = FlowFiberService::class)
    private val flowFiberService: FlowFiberService
) : PersistenceService, SingletonSerializeAsToken {

    private companion object {
        val log = contextLogger()
    }

    private val fiber: FlowFiber get() = flowFiberService.getExecutingFiber()

    @Suspendable
    override fun <R : Any> find(entityClass: Class<R>, primaryKey: Any): R? {
        return wrapResumedException {
            externalEventExecutor.execute(
                FindExternalEventFactory::class.java,
                FindParameters(entityClass, serialize(primaryKey))
            )
        }.firstOrNull()?.let { deserializeReceivedPayload(it.array(), entityClass) }
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
                return wrapResumedException {
                    externalEventExecutor.execute(
                        FindAllExternalEventFactory::class.java,
                        FindAllParameters(entityClass, 0, Int.MAX_VALUE)
                    )
                }.map { deserializeReceivedPayload(it.array(), entityClass) }
            }

            override fun setLimit(limit: Int): PagedQuery<R> {
                TODO("Not yet implemented")
            }

            override fun setOffset(offset: Int): PagedQuery<R> {
                TODO("Not yet implemented")
            }
        }
    }

    @Suspendable
    override fun <R : Any> merge(entity: R): R? {
        return wrapResumedException {
            externalEventExecutor.execute(
                MergeExternalEventFactory::class.java,
                MergeParameters(serialize(entity))
            )
        }.firstOrNull()?.let { deserializeReceivedPayload(it.array(), entity::class.java) }
    }

    @Suspendable
    override fun <R : Any> merge(entities: List<R>): List<R> {
        TODO("Not yet implemented")
    }

    @Suspendable
    override fun persist(entity: Any) {
        wrapResumedException {
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
        wrapResumedException {
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
        TODO("Not yet implemented")
    }

    @Suspendable
    private inline fun <T> wrapResumedException(function: () -> T): T {
        return try {
            function()
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