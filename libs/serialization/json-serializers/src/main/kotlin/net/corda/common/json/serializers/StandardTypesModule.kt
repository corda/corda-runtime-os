package net.corda.common.json.serializers

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.Module
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.module.SimpleModule
import net.corda.v5.base.types.MemberX500Name

internal object MemberX500NameDeserializer : JsonDeserializer<MemberX500Name>() {
    override fun deserialize(parser: JsonParser, ctxt: DeserializationContext?): MemberX500Name {
        return try {
            MemberX500Name.parse(parser.text)
        } catch (e: Exception) {
            throw JsonParseException(parser, e.message, e)
        }
    }
}
internal object MemberX500NameSerializer : JsonSerializer<MemberX500Name>() {
    override fun serialize(
        value: MemberX500Name,
        gen: JsonGenerator,
        serializers: SerializerProvider?
    ) {
        gen.writeString(value.toString())
    }
}

fun standardTypesModule(): Module {
    return SimpleModule("Standard types")
        .addDeserializer(MemberX500Name::class.java, MemberX500NameDeserializer)
        .addSerializer(MemberX500Name::class.java, MemberX500NameSerializer)
}
