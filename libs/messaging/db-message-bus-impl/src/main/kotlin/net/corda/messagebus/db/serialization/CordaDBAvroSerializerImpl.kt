package net.corda.messagebus.db.serialization

import net.corda.data.CordaAvroSerializer
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.v5.base.util.uncheckedCast

class CordaDBAvroSerializerImpl<T : Any>(
    private val schemaRegistry: AvroSchemaRegistry
) : CordaAvroSerializer<T> {

    override fun serialize(data: T): ByteArray {
        return when (data.javaClass) {
            ByteArray::class.java -> {
                uncheckedCast(data)
            }
            else -> {
                schemaRegistry.serialize(data).array()
            }
        }
    }
}
