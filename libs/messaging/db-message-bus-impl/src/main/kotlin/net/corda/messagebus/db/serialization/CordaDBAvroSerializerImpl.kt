package net.corda.messagebus.db.serialization

import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.slf4j.LoggerFactory
import java.util.UUID

class CordaDBAvroSerializerImpl<T : Any>(
    private val schemaRegistry: AvroSchemaRegistry,
    private val onError: ((ByteArray) -> Unit)?
) : CordaAvroSerializer<T> {

    companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun serialize(data: T): ByteArray? {

        try {
            return when (data) {
                is String -> (data as String).encodeToByteArray()
                is ByteArray -> data
                is UUID -> (data.toString()).encodeToByteArray()
                else -> schemaRegistry.serialize(data).array()
            }
        } catch (ex: Throwable) {
            val message = "Failed to serialize instance of class type ${data::class.java.name} containing $data"

            onError?.invoke(message.toByteArray())
            log.error(message, ex)
            throw CordaRuntimeException(message, ex)
        }
    }
}
