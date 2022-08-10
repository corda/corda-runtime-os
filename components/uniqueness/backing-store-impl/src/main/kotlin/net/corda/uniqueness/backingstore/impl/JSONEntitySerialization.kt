package net.corda.uniqueness.backingstore.impl

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import net.corda.v5.crypto.SecureHash

/**
 * This class contains various serializers and deserializers that are
 * required to store the Uniqueness Service result in the database
 * as a JSON format.
 */
@Suppress("ForbiddenComment")
// TODO: Remove this entirely and use standard Corda serialization libraries
fun jpaBackingStoreObjectMapper() = jacksonObjectMapper().apply {
    registerModule(JavaTimeModule())
    val module = SimpleModule()
    module.addSerializer(SecureHash::class.java, SecureHashSerializer)
    module.addDeserializer(SecureHash::class.java, SecureHashDeserializer)
    registerModule(module)
}

internal object SecureHashSerializer : JsonSerializer<SecureHash>() {
    override fun serialize(obj: SecureHash, generator: JsonGenerator, provider: SerializerProvider) {
        generator.writeString(obj.toString())
    }
}

internal object SecureHashDeserializer : JsonDeserializer<SecureHash>() {
    override fun deserialize(parser: JsonParser, ctxt: DeserializationContext): SecureHash {
        return SecureHash.create(parser.text)
    }
}
