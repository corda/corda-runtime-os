package net.corda.kryoserialization.impl

import net.corda.kryoserialization.CheckpointSerializationContext
import net.corda.kryoserialization.CheckpointSerializationService
import net.corda.kryoserialization.CheckpointSerializer
import net.corda.v5.base.types.sequence
import net.corda.v5.serialization.SerializedBytes

class CheckpointSerializationServiceImpl(
    private val context: CheckpointSerializationContext,
    private val serializer: CheckpointSerializer
) : CheckpointSerializationService {

    override fun <T : Any> serialize(obj: T): SerializedBytes<T> {
        return serializer.serialize(obj, context)
    }

    override fun <T : Any> deserialize(serializedBytes: SerializedBytes<T>, clazz: Class<T>): T {
        return serializer.deserialize(serializedBytes, clazz, context)
    }

    override fun <T : Any> deserialize(bytes: ByteArray, clazz: Class<T>): T {
        require(bytes.isNotEmpty()) { "Empty bytes" }
        return serializer.deserialize(bytes.sequence(), clazz, context)
    }
}