package net.corda.messagebus.db.serialization

import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.schema.registry.AvroSchemaRegistry
import org.slf4j.LoggerFactory

class CordaDBAvroSerializerImpl<T : Any>(
    private val schemaRegistry: AvroSchemaRegistry,
    private val throwOnSerializationError: Boolean,
    private val onError: ((ByteArray) -> Unit)?
) : CordaAvroSerializer<T> {

    companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun serialize(data: T): ByteArray? {
        return when (data) {
            is String -> (data as String).encodeToByteArray()
            is ByteArray -> data
            else -> {
                try {
                    schemaRegistry.serialize(data).array()
                } catch (ex: Throwable) {
                    val message = "Failed to serialize instance of class type ${data::class.java.name} containing $data"

                    onError?.invoke(message.toByteArray())
                    if(throwOnSerializationError) {
                        log.error(message, ex)
                        throw ex
                    } else {
                        log.warn(message, ex)
                        null
                    }
                }
            }
        }
    }
}
