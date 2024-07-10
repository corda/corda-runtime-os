package net.corda.ledger.lib.impl.stub.serialization

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.crypto.cipher.suite.KeyEncodingService
import java.security.PublicKey

class PublicKeyDeserializer : StdDeserializer<PublicKey>(PublicKey::class.java) {

    private val keyEncoding: KeyEncodingService = CipherSchemeMetadataImpl()

    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): PublicKey {
        val node = p.codec.readTree<JsonNode>(p)
        return keyEncoding.decodePublicKey(node.get("key").binaryValue())
    }
}