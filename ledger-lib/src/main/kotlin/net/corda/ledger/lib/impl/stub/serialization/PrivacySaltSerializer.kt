package net.corda.ledger.lib.impl.stub.serialization

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import net.corda.ledger.common.data.transaction.PrivacySalt

class PrivacySaltSerializer : StdSerializer<PrivacySalt>(PrivacySalt::class.java) {

    override fun serialize(value: PrivacySalt, gen: JsonGenerator, provider: SerializerProvider) {
        gen.writeStartObject()
        gen.writeBinaryField("salt", value.bytes)
        gen.writeEndObject()
    }
}