package net.corda.flow.application.serialization

import net.corda.flow.pipeline.exceptions.FlowFatalException
import net.corda.sandbox.type.SandboxConstants.CORDA_SYSTEM_SERVICE
import net.corda.sandbox.type.UsedByFlow
import net.corda.sandboxgroupcontext.CurrentSandboxGroupContext
import net.corda.sandboxgroupcontext.RequireSandboxAMQP.AMQP_SERIALIZATION_SERVICE
import net.corda.sandboxgroupcontext.getObjectByKey
import net.corda.utilities.reflection.castIfPossible
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.util.contextLogger
import net.corda.v5.serialization.SerializedBytes
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE
import java.io.NotSerializableException

@Component(
    service = [ SerializationService::class, SerializationServiceInternal::class, UsedByFlow::class ],
    property = [ CORDA_SYSTEM_SERVICE ],
    scope = PROTOTYPE
)
class SerializationServiceImpl @Activate constructor(
    @Reference(service = CurrentSandboxGroupContext::class)
    private val currentSandboxGroupContext: CurrentSandboxGroupContext
) : SerializationServiceInternal, UsedByFlow, SingletonSerializeAsToken {

    private companion object {
        private val log = contextLogger()
    }

    private val serializationService
        get(): SerializationService {
            return currentSandboxGroupContext.get().getObjectByKey(AMQP_SERIALIZATION_SERVICE)
                ?: throw FlowFatalException(
                    "The flow sandbox has not been initialized with an AMQP serializer for " +
                            "identity ${currentSandboxGroupContext.get().virtualNodeContext.holdingIdentity}"
                )
        }

    override fun <T : Any> serialize(obj: T): SerializedBytes<T> {
        return serializationService.serialize(obj)
    }

    override fun <T : Any> deserialize(bytes: ByteArray, clazz: Class<T>): T {
        return try {
            serializationService.deserialize(bytes, clazz)
        } catch (e: NotSerializableException) {
            log.error("Failed to deserialize it into a ${clazz.name}", e)
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

