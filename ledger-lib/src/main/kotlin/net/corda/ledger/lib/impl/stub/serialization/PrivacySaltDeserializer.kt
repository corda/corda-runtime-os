package net.corda.ledger.lib.impl.stub.serialization

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import net.corda.ledger.common.data.transaction.PrivacySalt
import net.corda.ledger.common.data.transaction.PrivacySaltImpl

class PrivacySaltDeserializer : StdDeserializer<PrivacySalt>(PrivacySalt::class.java) {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): PrivacySalt {
        val node = p.codec.readTree<JsonNode>(p)
        return PrivacySaltImpl(node.get("salt").binaryValue())
    }
}