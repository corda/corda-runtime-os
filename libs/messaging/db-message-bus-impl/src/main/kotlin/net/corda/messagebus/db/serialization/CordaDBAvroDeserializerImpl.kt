package net.corda.messagebus.db.serialization

import net.corda.data.CordaAvroDeserializer
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.uncheckedCast
import java.nio.ByteBuffer

class CordaDBAvroDeserializerImpl<T : Any>(
    private val schemaRegistry: AvroSchemaRegistry,
    private val onError: (ByteArray) -> Unit,
    private val expectedClass: Class<T>
) : CordaAvroDeserializer<T> {

    private companion object {
        val log = contextLogger()
    }

    override fun deserialize(data: ByteArray): T? {
        when (expectedClass) {
            ByteArray::class.java -> {
                return uncheckedCast(data)
            }
            else -> {
                return try {
                    val dataBuffer = ByteBuffer.wrap(data)
                    val classType = schemaRegistry.getClassType(dataBuffer)
                    uncheckedCast(schemaRegistry.deserialize(dataBuffer, classType, null))
                } catch (ex: Throwable) {
                    log.error("Failed to deserialise to expected class $expectedClass", ex)
                    // We don't want to throw back into Kafka as that would mean the entire poll (with possibly
                    // many records) would fail, and keep failing.  So we'll just callback to note the bad deserialize
                    // and return a null.  This will mean the record gets treated as 'deleted' in the processors
                    onError.invoke(data)
                    null
                }
            }
        }
    }
}
