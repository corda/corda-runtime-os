package net.corda.messagebus.db.serialization

import net.corda.data.CordaAvroSerializer
import net.corda.schema.registry.AvroSchemaRegistry

class CordaDBAvroSerializerImpl<T : Any>(
    private val schemaRegistry: AvroSchemaRegistry
) : CordaAvroSerializer<T> {

    override fun serialize(data: T): ByteArray {
        return when (data.javaClass) {
            String::class.java -> {
                (data as String).encodeToByteArray()
            }
            ByteArray::class.java -> data as ByteArray
            else -> {
                schemaRegistry.serialize(data).array()
            }
        }
    }
}
