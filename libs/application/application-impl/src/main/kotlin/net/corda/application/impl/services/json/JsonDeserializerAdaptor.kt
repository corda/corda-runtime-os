package net.corda.application.impl.services.json

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import net.corda.v5.application.marshalling.json.JsonDeserializer

/**
 * Adaptor between a Jackson deserializer and a Corda Json deserializer exposed to the public api. Every Json
 * deserializer written against Corda's public api is adapted to a Jackson deserializer and loaded into the Jackson
 * module. This class creates that simple bridge, wrapping the Corda serializer into a Jackson [StdDeserializer] subclass.
 */
class JsonDeserializerAdaptor<T : Any>(private val jsonDeserializer: JsonDeserializer<T>, clazz: Class<T>) :
    StdDeserializer<T>(clazz) {
    override fun deserialize(jsonParser: JsonParser, ctxt: DeserializationContext) =
        jsonDeserializer.deserialize(JsonNodeReaderAdaptor(jsonParser, ctxt))
}

inline fun <reified T : Any> jsonDeserializerAdaptorOf(jsonDeserializer: JsonDeserializer<T>) =
    JsonDeserializerAdaptor(jsonDeserializer, T::class.java)
