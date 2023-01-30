package net.corda.messagebus.db.serialization

import net.corda.data.CordaAvroDeserializer
import net.corda.schema.registry.AvroSchemaRegistry
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.util.function.Consumer

class CordaDBAvroDeserializerImpl<T : Any>(
    private val schemaRegistry: AvroSchemaRegistry,
    private val onError: Consumer<ByteArray>,
    private val expectedClass: Class<T>
) : CordaAvroDeserializer<T> {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun deserialize(data: ByteArray): T? {
        @Suppress("unchecked_cast")
        return when (expectedClass) {
            String::class.java -> {
                data.decodeToString() as T?
            }

            ByteArray::class.java -> {
                data as T?
            }

            else -> {
                try {
                    val dataBuffer = ByteBuffer.wrap(data)
                    // If they explicitly created a deserializer for Any then they should be able to handle
                    // the class they get back
                    val clazz = if (expectedClass != Any::class.java) {
                        expectedClass
                    } else {
                        schemaRegistry.getClassType(dataBuffer)
                    }
                    schemaRegistry.deserialize(dataBuffer, clazz, null) as T?
                } catch (ex: Throwable) {
                    log.warn("Failed to deserialise to expected class $expectedClass", ex)
                    // We don't want to throw back as that would mean the entire poll (with possibly
                    // many records) would fail, and keep failing.  So we'll just callback to note the bad deserialize
                    // and return a null.  This will mean the record gets treated as 'deleted' in the processors
                    onError.accept(data)
                    null
                }
            }
        }
    }
}
