package net.corda.flow.application.serialization

import java.io.NotSerializableException
import net.corda.flow.fiber.FlowFiberService
import net.corda.utilities.reflection.castIfPossible
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.util.contextLogger
import net.corda.v5.serialization.SerializedBytes
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE

@Component(
    service = [
        SerializationService::class,
        SerializationServiceInternal::class,
        SingletonSerializeAsToken::class
    ],
    scope = PROTOTYPE,
    property = [ "corda.system=true" ]
)
class SerializationServiceImpl @Activate constructor(
    @Reference(service = FlowFiberService::class)
    private val flowFiberService: FlowFiberService
) : SerializationServiceInternal, SingletonSerializeAsToken {

    private companion object {
        private val log = contextLogger()
    }

    private val serializationService
        get(): SerializationService {
            return flowFiberService.getExecutingFiber().getExecutionContext().run {
                sandboxGroupContext.amqpSerializer
            }
        }

    override fun <T : Any> serialize(obj: T): SerializedBytes<T> {
        return serializationService.serialize(obj)
    }

    override fun <T : Any> deserialize(bytes: ByteArray, clazz: Class<T>): T {
        return try {
            serializationService.deserialize(bytes, clazz)
        } catch (e: NotSerializableException) {
            log.info("Failed to deserialize it into a ${clazz.name}", e)
            throw e
        }
    }

    override fun <T : Any> deserialize(serializedBytes: SerializedBytes<T>, clazz: Class<T>): T {
        return deserialize(serializedBytes.bytes, clazz)
    }

    override fun <T : Any> deserializeAndCheckType(bytes: ByteArray, clazz: Class<T>): T {
        return deserialize(bytes, clazz).also { checkDeserializedObjectIs(it, clazz) }
    }

    override fun <T : Any> deserializeAndCheckType(serializedBytes: SerializedBytes<T>, clazz: Class<T>): T {
        return deserialize(serializedBytes, clazz).also { checkDeserializedObjectIs(it, clazz) }
    }

    /**
     * AMQP deserialization outputs an object whose type is solely based on the serialized content, therefore although
     * the generic type is specified, it can still be the wrong type. We check this type here, so that we can throw an
     * accurate error instead of failing later on when the object is used.
     */
    private fun <T : Any> checkDeserializedObjectIs(deserialized: Any, clazz: Class<T>): T {
        return clazz.castIfPossible(deserialized) ?: throw DeserializedWrongAMQPObjectException(
            expectedType = clazz,
            deserializedType = deserialized.javaClass,
            deserializedObject = deserialized,
            "Expected to deserialize into a ${clazz.name} but was a ${deserialized.javaClass.name} instead, object: ($deserialized)"
        )
    }
}