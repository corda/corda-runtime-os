package net.corda.messagebus.kafka.serialization

import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.apache.kafka.common.serialization.Serializer
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Corda avro serializer impl
 *
 * @param T Type to serialize
 * @property schemaRegistry the Avro-based Schemas
 * @property throwOnSerializationError throw exception or return null on failure to serialize (defaults to throw)
 * @property onError lambda to be run on serialization error
 */
class CordaAvroSerializerImpl<T : Any>(
    private val schemaRegistry: AvroSchemaRegistry,
    private val onError: ((ByteArray) -> Unit)?
) : CordaAvroSerializer<T>, Serializer<T> {

    companion object {
        private val stringSerializer = StringSerializer()
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    /**
     * Serialize data T.
     * On serialization failure if throwOnSerializationError is true, throw and exception. If false, return null
     * if onError is not null, run the lambda
     *
     * @param data
     * @return Serialized data or null
     */
    override fun serialize(data: T): ByteArray? {
        try {
            return when (data) {
                is String -> stringSerializer.serialize(null, data)
                is ByteArray -> data
                is UUID -> stringSerializer.serialize(null, data.toString())
                else -> {
                    schemaRegistry.serialize(data).array()
                }
            }
        } catch (ex: Throwable) {
            val message = "Failed to serialize instance of class type ${data::class.java.name} containing $data"

            onError?.invoke(message.toByteArray())
            log.error(message, ex)
            throw CordaRuntimeException(message, ex)
        }
    }


    override fun serialize(topic: String?, data: T?): ByteArray? {
        return when (data) {
            null -> null
            else -> serialize(data)
        }
    }
}
