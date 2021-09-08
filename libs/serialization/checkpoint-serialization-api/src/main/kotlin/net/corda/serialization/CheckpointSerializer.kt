package net.corda.serialization

import java.io.NotSerializableException

interface CheckpointSerializer {
    @Throws(NotSerializableException::class)
    fun <T : Any> deserialize(bytes: ByteArray, clazz: Class<T>): T

    @Throws(NotSerializableException::class)
    fun <T : Any> serialize(obj: T): ByteArray
}
