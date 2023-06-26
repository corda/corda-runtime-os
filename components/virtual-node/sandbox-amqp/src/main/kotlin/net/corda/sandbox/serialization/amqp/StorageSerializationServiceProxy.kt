package net.corda.sandbox.serialization.amqp

import net.corda.internal.serialization.StorageSerializationService
import net.corda.sandbox.type.SandboxConstants.AMQP_STORAGE
import net.corda.sandbox.type.SandboxConstants.CORDA_UNINJECTABLE_SERVICE
import net.corda.sandbox.type.UsedByPersistence
import net.corda.sandbox.type.UsedByVerification
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.serialization.SerializedBytes
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE

/**
 * A [SerializationService] component for providing
 * [AMQP_STORAGE_CONTEXT][net.corda.internal.serialization.AMQP_STORAGE_CONTEXT] serialization
 * for persistence and verification sandboxes.
 */
@Component(
    service = [
        SerializationService::class,
        StorageSerializationService::class,
        StorageSerializationServiceProxy::class,
        UsedByPersistence::class,
        UsedByVerification::class
    ],
    property = [ AMQP_STORAGE, CORDA_UNINJECTABLE_SERVICE ],
    scope = PROTOTYPE
)
class StorageSerializationServiceProxy : StorageSerializationService, UsedByPersistence, UsedByVerification {
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
