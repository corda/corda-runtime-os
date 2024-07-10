package net.corda.ledger.lib.impl.stub.serialization

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.crypto.cipher.suite.KeyEncodingService
import java.security.PublicKey

class PublicKeySerializer : StdSerializer<PublicKey>(PublicKey::class.java) {

    private val keyEncoding: KeyEncodingService = CipherSchemeMetadataImpl()

    override fun serialize(value: PublicKey, gen: JsonGenerator, provider: SerializerProvider) {
        gen.writeStartObject()
        gen.writeBinaryField("key", keyEncoding.encodeAsByteArray(value))
        gen.writeEndObject()
    }
}
