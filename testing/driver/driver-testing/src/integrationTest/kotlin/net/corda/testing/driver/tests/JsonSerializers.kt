package net.corda.testing.driver.tests

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializerProvider
import net.corda.crypto.core.parseSecureHash
import net.corda.v5.crypto.SecureHash

object SecureHashSerializer : JsonSerializer<SecureHash>() {
    override fun serialize(obj: SecureHash, generator: JsonGenerator, provider: SerializerProvider) {
        generator.writeString(obj.toString())
    }
}

object SecureHashDeserializer : JsonDeserializer<SecureHash>() {
    override fun deserialize(parser: JsonParser, ctxt: DeserializationContext): SecureHash {
        return parseSecureHash(parser.text)
    }
}

fun ObjectMapper.readAsMap(json: String): Map<String, Any> {
    return readValue(json, object : TypeReference<Map<String, Any>>() {})
}
