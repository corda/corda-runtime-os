package net.corda.httprpc.server.impl.rpcops

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.httprpc.RpcOps
import net.corda.httprpc.annotations.HttpRpcPOST
import net.corda.httprpc.annotations.HttpRpcRequestBodyParameter
import net.corda.httprpc.annotations.HttpRpcResource

@HttpRpcResource(path = "customjson")
interface CustomSerializationAPI : RpcOps {
    @HttpRpcPOST(path = "print")
    fun printString(@HttpRpcRequestBodyParameter s: CustomString): CustomString

    @HttpRpcPOST(path = "printcustommarshal")
    fun printCustomMarshalString(@HttpRpcRequestBodyParameter s: CustomMarshalString): CustomMarshalString

    @HttpRpcPOST(path = "unsafe")
    fun printUnsafeString(@HttpRpcRequestBodyParameter s: CustomUnsafeString): CustomUnsafeString
}

@JsonSerialize(using = CustomSerializer::class)
@JsonDeserialize(using = CustomDeserializer::class)
@CordaSerializable
class CustomString(val s: String)

@CordaSerializable
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
@CordaSerializable
class CustomMarshalString(val s: String)

class CustomNonSerializableString(val unsafe: String) : CustomUnsafeString(unsafe) {
    companion object {
        var flag: Boolean = false
    }

    init {
        flag = true
    }
}
class CustomSerializer: JsonSerializer<CustomString>() {
    override fun serialize(value: CustomString?, gen: JsonGenerator?, serializers: SerializerProvider?) {
        gen?.writeRawValue("\"custom ${value?.s}\"")
    }
}

class CustomDeserializer: JsonDeserializer<CustomString>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): CustomString {
        val s = p.valueAsString
        return CustomString(s)
    }
}

class CustomMarshalStringSerializer: JsonSerializer<CustomMarshalString>() {
    override fun serialize(value: CustomMarshalString?, gen: JsonGenerator?, serializers: SerializerProvider?) {
        gen?.writeStartObject()
        gen?.writeStringField("data",  "custom ${value?.s}")
        gen?.writeEndObject()
    }
}

class CustomMarshalStringDeserializer: JsonDeserializer<CustomMarshalString>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): CustomMarshalString {
        val s = p.valueAsString
        return CustomMarshalString(s)
    }
}