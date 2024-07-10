package net.corda.ledger.lib.impl.stub.serialization

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import net.corda.ledger.common.data.transaction.TransactionMetadataImpl
import net.corda.v5.ledger.common.transaction.TransactionMetadata

class TransactionMetadataDeserializer : StdDeserializer<TransactionMetadata>(TransactionMetadata::class.java) {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): TransactionMetadata {
        // TODO IMPLEMENT
        return TransactionMetadataImpl(emptyMap())
    }
}