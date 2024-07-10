package net.corda.ledger.lib.impl.stub.serialization

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import net.corda.v5.ledger.common.transaction.TransactionMetadata

class TransactionMetadataSerializer : StdSerializer<TransactionMetadata>(TransactionMetadata::class.java) {
    override fun serialize(value: TransactionMetadata, gen: JsonGenerator, provider: SerializerProvider) {
        gen.writeStartObject()
        gen.writeStringField("ledgerModel", value.ledgerModel)
        gen.writeNumberField("ledgerVersion", value.ledgerVersion)
        gen.writeStringField("transactionSubtype", value.transactionSubtype)
        gen.writeNumberField("platformVersion", value.platformVersion)
        gen.writeObjectField("digestSettings", value.digestSettings)
        gen.writeEndObject()
    }
}