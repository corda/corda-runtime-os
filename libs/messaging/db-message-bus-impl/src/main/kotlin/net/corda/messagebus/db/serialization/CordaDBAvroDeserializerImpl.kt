package net.corda.messagebus.db.serialization

import net.corda.data.CordaAvroDeserializer
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.v5.base.util.contextLogger
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
        @Suppress("unchecked_cast")
        return when (expectedClass) {
            String::class.java -> {
                data.decodeToString()
            }
            ByteArray::class.java -> {
                data
            }
            else -> {
                try {
                    val dataBuffer = ByteBuffer.wrap(data)
                    val classType = schemaRegistry.getClassType(dataBuffer)
                    schemaRegistry.deserialize(dataBuffer, classType, null)
                } catch (ex: Throwable) {
                    log.error("Failed to deserialise to expected class $expectedClass", ex)
                    // We don't want to throw back as that would mean the entire poll (with possibly
                    // many records) would fail, and keep failing.  So we'll just callback to note the bad deserialize
                    // and return a null.  This will mean the record gets treated as 'deleted' in the processors
                    onError.invoke(data)
                    null
                }
            }
        } as? T?
    }
}
