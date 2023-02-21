package net.corda.httprpc.test

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import net.corda.httprpc.RestResource
import net.corda.httprpc.annotations.HttpPOST
import net.corda.httprpc.annotations.ClientRequestBodyParameter
import net.corda.httprpc.annotations.HttpRestResource

@HttpRestResource(path = "customjson")
interface CustomSerializationAPI : RestResource {
    @HttpPOST(path = "print")
    fun printString(@ClientRequestBodyParameter s: CustomString): CustomString

    @HttpPOST(path = "printcustommarshal")
    fun printCustomMarshalString(@ClientRequestBodyParameter s: CustomMarshalString): CustomMarshalString

    @HttpPOST(path = "unsafe")
    fun printUnsafeString(@ClientRequestBodyParameter s: CustomUnsafeString): CustomUnsafeString
}

@JsonSerialize(using = CustomSerializer::class)
@JsonDeserialize(using = CustomDeserializer::class)
class CustomString(val s: String)

open class CustomUnsafeString(val data: String) {
    companion object {
        var flag: Boolean = false
    }

    init {
        flag = true
    }
}

@JsonSerialize(using = CustomMarshalStringSerializer::class)
@JsonDeserialize(using = CustomMarshalStringDeserializer::class)
class CustomMarshalString(val s: String)

class CustomNonSerializableString(val unsafe: String) : CustomUnsafeString(unsafe) {
    companion object {
        var flag: Boolean = false
    }

    init {
        flag = true
    }
}

class CustomSerializer : JsonSerializer<CustomString>() {
    override fun serialize(value: CustomString?, gen: JsonGenerator?, serializers: SerializerProvider?) {
        gen?.writeRawValue("\"custom ${value?.s}\"")
    }
}

class CustomDeserializer : JsonDeserializer<CustomString>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): CustomString {
        val s = p.valueAsString
        return CustomString(s)
    }
}

class CustomMarshalStringSerializer : JsonSerializer<CustomMarshalString>() {
    override fun serialize(value: CustomMarshalString?, gen: JsonGenerator?, serializers: SerializerProvider?) {
        gen?.writeStartObject()
        gen?.writeStringField("data", "custom ${value?.s}")
        gen?.writeEndObject()
    }
}

class CustomMarshalStringDeserializer : JsonDeserializer<CustomMarshalString>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): CustomMarshalString {
        val s = p.valueAsString
        return CustomMarshalString(s)
    }
}