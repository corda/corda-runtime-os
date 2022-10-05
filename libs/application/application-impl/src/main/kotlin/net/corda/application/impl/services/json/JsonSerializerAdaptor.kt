package net.corda.application.impl.services.json

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import net.corda.v5.application.marshalling.json.JsonSerializer

class JsonSerializerAdaptor<T>(private val jsonSerializer: JsonSerializer<T>, clazz: Class<T>) :
    StdSerializer<T>(clazz) {
    override fun serialize(
        value: T, jgen: JsonGenerator, provider: SerializerProvider
    ) {
        val jsonWriterAdaptor = JsonWriterAdaptor(jgen)
        jsonSerializer.serialize(value, jsonWriterAdaptor);
    }
}

inline fun <reified T> jsonSerializerAdaptorOf(jsonSerializer: JsonSerializer<T>) =
    JsonSerializerAdaptor<T>(jsonSerializer, T::class.java)
