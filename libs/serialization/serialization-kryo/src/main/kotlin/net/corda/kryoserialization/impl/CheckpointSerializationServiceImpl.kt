package net.corda.kryoserialization.impl

import net.corda.serialization.CheckpointSerializationService
import net.corda.serialization.CheckpointSerializer

class CheckpointSerializationServiceImpl(
    private val serializer: CheckpointSerializer
) : CheckpointSerializationService {

    override fun <T : Any> serialize(obj: T): ByteArray {
        return serializer.serialize(obj)
    }

    override fun <T : Any> deserialize(bytes: ByteArray, clazz: Class<T>): T {
        return serializer.deserialize(bytes, clazz)
    }
}
