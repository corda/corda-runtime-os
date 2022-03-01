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
    fun <T> toAvro(): T = toAny() as T

    fun toAny(): Any {
        val clss = Class.forName(schemaClass)
        val method = clss.getMethod("fromByteBuffer", ByteBuffer::class.java)
        return method.invoke(null, ByteBuffer.wrap(schema))
    }
}

private fun ByteBuffer.toByteArray(): ByteArray {
    val array = ByteArray(remaining())
    get(array)
    return array
}
