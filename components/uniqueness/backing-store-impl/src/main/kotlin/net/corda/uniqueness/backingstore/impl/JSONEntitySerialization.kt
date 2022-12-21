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
import net.corda.uniqueness.datamodel.serialize.UniquenessCheckErrorTypeMixin
import net.corda.uniqueness.datamodel.serialize.UniquenessCheckStateDetailsTypeMixin
import net.corda.uniqueness.datamodel.serialize.UniquenessCheckStateRefTypeMixin
import net.corda.v5.application.uniqueness.model.UniquenessCheckError
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.uniqueness.data.UniquenessCheckStateDetails

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

    addMixIn(UniquenessCheckError::class.java, UniquenessCheckErrorTypeMixin::class.java)
    addMixIn(UniquenessCheckStateDetails::class.java, UniquenessCheckStateDetailsTypeMixin::class.java)
    addMixIn(StateRef::class.java, UniquenessCheckStateRefTypeMixin::class.java)
}

internal object SecureHashSerializer : JsonSerializer<SecureHash>() {
    override fun serialize(obj: SecureHash, generator: JsonGenerator, provider: SerializerProvider) {
        generator.writeString(obj.toString())
    }
}

internal object SecureHashDeserializer : JsonDeserializer<SecureHash>() {
    override fun deserialize(parser: JsonParser, ctxt: DeserializationContext): SecureHash {
        return SecureHash.parse(parser.text)
    }
}
