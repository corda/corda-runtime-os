package net.corda.flow.fiber

import java.io.NotSerializableException
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.util.castIfPossible
import net.corda.v5.base.util.contextLogger
import net.corda.v5.serialization.SerializedBytes
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [FlowFiberSerializationService::class, SingletonSerializeAsToken::class])
class FlowFiberSerializationServiceImpl @Activate constructor(
    @Reference(service = FlowFiberService::class)
    private val flowFiberService: FlowFiberService
) : FlowFiberSerializationService, SingletonSerializeAsToken {

    private companion object {
        private val log = contextLogger()
    }

    private val serializationService
        get(): SerializationService {
            return flowFiberService.getExecutingFiber().getExecutionContext().run {
                sandboxGroupContext.amqpSerializer
            }
        }

    override fun <R : Any> deserialize(bytes: ByteArray, expectedType: Class<R>): R {
        return try {
            val payload = serializationService.deserialize(bytes, expectedType)
            checkDeserializedObjectIs(payload, expectedType)
            payload
        } catch (e: NotSerializableException) {
            log.info("Failed to deserialize it into a ${expectedType.name}", e)
            throw e
        }
    }

    /**
     * AMQP deserialization outputs an object whose type is solely based on the serialized content, therefore although
     * the generic type is specified, it can still be the wrong type. We check this type here, so that we can throw an
     * accurate error instead of failing later on when the object is used.
     */
    private fun <R : Any> checkDeserializedObjectIs(deserialized: Any, expectedType: Class<R>) {
        expectedType.castIfPossible(deserialized) ?: throw DeserializedWrongAMQPObjectException(
            expectedType = expectedType,
            deserializedType = deserialized.javaClass,
            deserializedObject = deserialized,
            "Expected to deserialize into a ${expectedType.name} but was a ${deserialized.javaClass.name} instead, object: ($deserialized)"
        )
    }

    override fun <T : Any> serialize(obj: T): SerializedBytes<T> {
        return serializationService.serialize(obj)
    }
}