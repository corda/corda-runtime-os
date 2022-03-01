package net.corda.introspiciere.domain

import java.nio.ByteBuffer

data class KafkaMessage(
    val topic: String,
    val key: String?,
    val schema: ByteArray,
    val schemaClass: String,
) {
    companion object {
        fun create(topic: String, key: String?, schema: Any): KafkaMessage {
            val byteBuffer = schema::class.java.getMethod("toByteBuffer").invoke(schema) as ByteBuffer
            return KafkaMessage(topic, key, byteBuffer.toByteArray(), schema::class.qualifiedName!!)
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> toAvro(clazz: Class<T>): T {
        val fromByteBuffer = clazz.getMethod("fromByteBuffer", ByteBuffer::class.java)
        return fromByteBuffer.invoke(null, ByteBuffer.wrap(schema)) as T
    }
}

private fun ByteBuffer.toByteArray(): ByteArray {
    val array = ByteArray(remaining())
    get(array)
    return array
}
