package net.corda.httprpc.server.impl.rpcops.impl

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.module.SimpleModule
import io.javalin.apibuilder.ApiBuilder.path
import io.javalin.apibuilder.ApiBuilder.post
import io.javalin.http.Context
import net.corda.v5.httprpc.api.Controller

class CustomSerializationControllerImpl : Controller {

    override fun register() {
        path("/customjson") {
            post("/print", ::print)
            post("/printcustommarshal", ::printCustomMarshalString)
            post("/unsafe", ::printUnsafeString)
        }
    }

    private val mapper = ObjectMapper().apply {
        this.registerModule(
            object : SimpleModule("my-module") {
                init {
                    addDeserializer(CustomMarshalString::class.java, CustomMarshalStringDeserializer())
                }
            }
        )
    }

    private fun print(ctx: Context) {
        ctx.json(ctx.bodyAsClass(CustomString::class.java))
    }

    private fun printCustomMarshalString(ctx: Context) {
//        val string = ctx.body()
//        mapper.readValue(string, CustomMarshalString::class.java)
        ctx.json(ctx.bodyAsClass(CustomMarshalString::class.java))
    }

    private fun printUnsafeString(ctx: Context) {
        ctx.json(ctx.bodyAsClass(CustomUnsafeString::class.java))
    }
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