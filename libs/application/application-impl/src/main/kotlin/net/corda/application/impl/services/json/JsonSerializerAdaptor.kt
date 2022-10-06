package net.corda.application.impl.services.json

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import net.corda.v5.application.marshalling.json.JsonSerializer

/**
 * Adaptor between a Jackson serializer and a Corda Json serializer exposed to the public api. Every Json serializer
 * written against Corda's public api is adapted to a Jackson serializer and loaded into the Jackson module. This
 * class creates that simple bridge, wrapping the Corda serializer into a Jackson [StdSerializer] subclass.
 */
class JsonSerializerAdaptor<T : Any>(private val jsonSerializer: JsonSerializer<T>, clazz: Class<T>) :
    StdSerializer<T>(clazz) {
    override fun serialize(
        value: T, jgen: JsonGenerator, provider: SerializerProvider
    ) = jsonSerializer.serialize(value, JsonWriterAdaptor(jgen))
}

inline fun <reified T : Any> jsonSerializerAdaptorOf(jsonSerializer: JsonSerializer<T>) =
    JsonSerializerAdaptor(jsonSerializer, T::class.java)
