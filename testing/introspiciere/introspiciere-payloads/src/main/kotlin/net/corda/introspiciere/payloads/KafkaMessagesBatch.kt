package net.corda.introspiciere.payloads

import java.nio.ByteBuffer

data class KafkaMessagesBatch(
    val schemaClass: String,
    val messages: List<ByteArray>,
    val latestOffsets: LongArray,
)

data class Msg(
    val timestamp: Long,
    val key: String?,
    val data: ByteArray,
)

data class MsgBatch(
    val schema: String,
    val messages: List<Msg>,
    val nextBatchTimestamp: Long,
)

inline fun <reified T> Msg.deserialize(): T = deserialize(T::class.java)

@Suppress("UNCHECKED_CAST")
fun <T> Msg.deserialize(clazz: Class<T>): T {
    val fromByteBuffer = clazz.getMethod("fromByteBuffer", ByteBuffer::class.java)
    return fromByteBuffer.invoke(null, ByteBuffer.wrap(data)) as T
}