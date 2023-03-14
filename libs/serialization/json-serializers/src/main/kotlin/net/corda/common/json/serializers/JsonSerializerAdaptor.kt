package net.corda.common.json.serializers

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import net.corda.v5.application.marshalling.json.JsonSerializer
import net.corda.v5.application.marshalling.json.JsonWriter

/**
 * Adaptor between a Jackson serializer and a Corda Json serializer exposed to the public api. Every Json serializer
 * written against Corda's public api is adapted to a Jackson serializer and loaded into the Jackson module. This
 * class creates that simple bridge, wrapping the Corda serializer into a Jackson [StdSerializer] subclass.
 *
 * Because JsonSerializers are created at runtime dynamically, no compile time type information can be referenced in
 * this class in the form of generics. Instead, all type information is supplied only via a Class<*> object.
 */
@Suppress("unchecked_cast")
class JsonSerializerAdaptor(private val jsonSerializer: JsonSerializer<*>, val serializingType: Class<*>) :
    StdSerializer<Any>(serializingType as Class<Any>) {
    override fun serialize(value: Any, jgen: JsonGenerator, provider: SerializerProvider) {
        serializeAny(jsonSerializer, value, JsonWriterAdaptor(jgen))
    }

    /**
     * Note to maintainers. Here it looks like value as T is being inferred from the type of JsonSerializer<T> which
     * appears to defy erasure, however it's really some compiler synthetic code which makes this work.
     *
     * When you inherit from JsonSerializer<T>, the compiler converts it to JsonSerializer<Object> whatever T is, under
     * the rules of erasure. Your class declaration has of course an override function which is strongly typed. In order
     * that this strongly typed method can be called from an interface which only understands Object types, the compiler
     * generates a synthetic bridge function which takes an Object, casts it to your strong type, and then calls your
     * override.
     *
     * So in the code below the "value as T" is never actually called in this method, instead it simply calls the
     * synthetic method passing value as an Object type. Your own class then does the cast in the bridge function. There
     * is no type inference here, it's just syntactic sugar for a dispatch pattern implemented on your behalf by the
     * compiler.
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T> serializeAny(jsonSerializer: JsonSerializer<T>, value: Any, jsonWriter: JsonWriter) =
        jsonSerializer.serialize(value as T, jsonWriter)
}
