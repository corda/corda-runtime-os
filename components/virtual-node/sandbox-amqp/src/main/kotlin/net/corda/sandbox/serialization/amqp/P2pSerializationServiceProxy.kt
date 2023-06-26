package net.corda.sandbox.serialization.amqp

import net.corda.internal.serialization.P2pSerializationService
import net.corda.sandbox.type.SandboxConstants.AMQP_P2P
import net.corda.sandbox.type.SandboxConstants.CORDA_UNINJECTABLE_SERVICE
import net.corda.sandbox.type.UsedByFlow
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.serialization.SerializedBytes
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE

/**
 * A [SerializationService] component for providing
 * [AMQP_P2P_CONTEXT][net.corda.internal.serialization.AMQP_P2P_CONTEXT] serialization
 * to flow sandboxes.
 */
@Component(
    service = [
        P2pSerializationService::class,
        P2pSerializationServiceProxy::class,
        UsedByFlow::class
    ],
    property = [ AMQP_P2P, CORDA_UNINJECTABLE_SERVICE ],
    scope = PROTOTYPE
)
class P2pSerializationServiceProxy : P2pSerializationService, SingletonSerializeAsToken, UsedByFlow {
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
