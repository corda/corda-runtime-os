package net.corda.sandbox.serialization.amqp

import net.corda.sandbox.type.SandboxConstants.CORDA_SYSTEM_SERVICE
import net.corda.sandbox.type.UsedByFlow
import net.corda.sandbox.type.UsedByPersistence
import net.corda.sandbox.type.UsedByVerification
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.serialization.SerializedBytes
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE

/**
 * A [SerializationService] component for flow, persistence and verification sandboxes.
 * Flow sandboxes expect to be able to `@CordaInject` this [SerializationService], either
 * directly or via a `@CordaSystemFlow`, and so we also declare it as a "system service".
 */
@Component(
    service = [
        SerializationService::class,
        SerializationServiceProxy::class,
        UsedByFlow::class,
        UsedByPersistence::class,
        UsedByVerification::class
    ],
    property = [ CORDA_SYSTEM_SERVICE ],
    scope = PROTOTYPE
)
class SerializationServiceProxy
    : SerializationService, SingletonSerializeAsToken, UsedByFlow, UsedByPersistence, UsedByVerification {
    private var serializationService: SerializationService? = null

    fun wrap(serializer: SerializationService) {
        serializationService = serializer
    }

    override fun <T : Any> deserialize(bytes: ByteArray, clazz: Class<T>): T {
        return checkNotNull(serializationService) { "deserialize(byte[], Class): Not initialised" }
            .deserialize(bytes, clazz)
    }

    override fun <T : Any> deserialize(serializedBytes: SerializedBytes<T>, clazz: Class<T>): T {
        return checkNotNull(serializationService) { "deserialize(SerializedBytes, Class): Not initialized" }
            .deserialize(serializedBytes, clazz)
    }

    override fun <T : Any> serialize(obj: T): SerializedBytes<T> {
        return checkNotNull(serializationService) { "serialize(Object): Not initialized" }
            .serialize(obj)
    }
}
