package net.corda.httprpc.client.serialization

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.BeanProperty
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.deser.ContextualDeserializer
import com.fasterxml.jackson.databind.module.SimpleModule
import net.corda.common.json.serialization.jacksonObjectMapper
import net.corda.httprpc.JsonObject
import net.corda.httprpc.durablestream.DurableCursorTransferObject
import net.corda.httprpc.durablestream.api.Cursor
import net.corda.utilities.trace
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.SecureHash
import org.slf4j.LoggerFactory

internal val objectMapper = jacksonObjectMapper().apply {
    val module = SimpleModule("Durable cursor related")
    module.addDeserializer(Cursor.PollResult.PositionedValue::class.java, PositionedValueDeserializer())
    module.addDeserializer(SecureHash::class.java, SecureHashDeserializer)
    module.addDeserializer(MemberX500Name::class.java, MemberX500NameDeserializer)
    module.addSerializer(JsonObject::class.java, JsonObjectSerializer)
    this.registerModule(module)
}

// required because jackson can't deserialize to abstract type (interface)
internal class PositionedValueDeserializer(private val valueType: JavaType? = null) :
    JsonDeserializer<Cursor.PollResult.PositionedValue<*>>(), ContextualDeserializer {

    override fun createContextual(ctxt: DeserializationContext, property: BeanProperty?): JsonDeserializer<*> {
        val newValueType = ctxt.contextualType.containedType(0)
        // we return a new deserializer for any new contextual types
        return PositionedValueDeserializer(newValueType)
    }

    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Cursor.PollResult.PositionedValue<*> {
        val jacksonType = ctxt.typeFactory.constructParametricType(
            DurableCursorTransferObject.Companion.PositionedValueImpl::class.java, valueType
        )
        return ctxt.findRootValueDeserializer(jacksonType).deserialize(p, ctxt) as Cursor.PollResult.PositionedValue<*>
    }
}

/**
 * Needed or else the following exception will be thrown:
 * com.fasterxml.jackson.databind.exc.InvalidDefinitionException: Cannot construct instance of `net.corda.v5.crypto.SecureHash`
 * (no Creators, like default constructor, exist): abstract types either need to be mapped to concrete types, have custom deserializer,
 * or contain additional type information
 */
internal object SecureHashDeserializer : JsonDeserializer<SecureHash>() {

    private val log = LoggerFactory.getLogger(this::class.java)

    override fun deserialize(parser: JsonParser, context: DeserializationContext): SecureHash {
        log.trace { "Deserialize." }
        try {
            return SecureHash.parse(parser.text)
                .also { log.trace { "Deserialize completed." } }
        } catch (e: Exception) {
            "Invalid hash ${parser.text}: ${e.message}".let {
                log.error(it)
                throw JsonParseException(parser, it, e)
            }
        }
    }
}

internal object MemberX500NameDeserializer : JsonDeserializer<MemberX500Name>() {
    private val log = LoggerFactory.getLogger(this::class.java)
    override fun deserialize(parser: JsonParser, context: DeserializationContext): MemberX500Name {
        log.trace { "Deserialize." }
        return try {
            MemberX500Name.parse(parser.text)
        } catch (e: IllegalArgumentException) {
            "Invalid Corda X.500 name ${parser.text}: ${e.message}".let {
                log.error(it)
                throw JsonParseException(parser, it, e)
            }
        }.also { log.trace { "Deserialize completed." } }
    }
}

internal object JsonObjectSerializer : JsonSerializer<JsonObject>() {
    override fun serialize(obj: JsonObject, generator: JsonGenerator, provider: SerializerProvider) {
        generator.writeString(obj.escapedJson)
    }
}