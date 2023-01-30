package net.corda.messagebus.kafka.serialization

import net.corda.data.CordaAvroDeserializer
import net.corda.schema.registry.AvroSchemaRegistry
import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.util.function.Consumer

class CordaAvroDeserializerImpl<T : Any>(
    private val schemaRegistry: AvroSchemaRegistry,
    private val onError: Consumer<ByteArray>,
    private val expectedClass: Class<T>
) : CordaAvroDeserializer<T>, Deserializer<T> {

    private companion object {
        val stringDeserializer = StringDeserializer()
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun deserialize(data: ByteArray): T? {
        @Suppress("unchecked_cast")
        return when (expectedClass) {
            String::class.java -> {
                stringDeserializer.deserialize(null, data) as T?
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
                    // We don't want to throw back into Kafka as that would mean the entire poll (with possibly
                    // many records) would fail, and keep failing.  So we'll just callback to note the bad deserialize
                    // and return a null.  This will mean the record gets treated as 'deleted' in the processors
                    onError.accept(data)
                    null
                }
            }
        }
    }

    override fun deserialize(topic: String?, data: ByteArray?): T? {
        return when(data) {
            null -> null
            else -> deserialize(data)
        }
    }
}
