package net.corda.messagebus.kafka.serialization

import java.util.function.Consumer
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.schema.registry.AvroSchemaRegistry
import org.apache.kafka.common.serialization.Serializer
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.LoggerFactory

class CordaAvroSerializerImpl<T : Any>(
    private val schemaRegistry: AvroSchemaRegistry,
    private val throwOnError: Boolean,
    private val onError: Consumer<ByteArray>?
) : CordaAvroSerializer<T>, Serializer<T> {

    companion object {
        private val stringSerializer = StringSerializer()
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun serialize(data: T): ByteArray? {
        return when (data) {
            is String -> stringSerializer.serialize(null, data)
            is ByteArray -> data
            else -> {
                try {
                    schemaRegistry.serialize(data).array()
                } catch (ex: Throwable) {
                    // We don't want to throw as that would mean the entire poll (with possibly
                    // many records) would fail, and keep failing. So we'll just callback to note the serialize
                    // and return a null.  This will mean the record gets treated as 'deleted' in the processors
                    val message = "Failed to serialize instance of class type ${data::class.java.name} containing " +
                                "$data"

                    if(throwOnError) {
                        log.error(message, ex)
                        runOnErrorLambda(message, onError)
                        throw ex
                    } else {
                        log.warn(message, ex)
                        runOnErrorLambda(message, onError)
                        null
                    }
                }
            }
        }
    }

    override fun serialize(topic: String?, data: T?): ByteArray? {
        return when (data) {
            null -> null
            else -> serialize(data)
        }
    }

    private fun runOnErrorLambda(message: String, onError: Consumer<ByteArray>?) {
        onError?.accept(message.toByteArray())
    }
}
