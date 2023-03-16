package net.corda.common.json.serializers

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import net.corda.v5.application.marshalling.json.JsonDeserializer

/**
 * Adaptor between a Jackson deserializer and a Corda Json deserializer exposed to the public api. Every Json
 * deserializer written against Corda's public api is adapted to a Jackson deserializer and loaded into the Jackson
 * module. This class creates that simple bridge, wrapping the Corda serializer into a Jackson [StdDeserializer] subclass.
 *
 * Because JsonSerializers are created at runtime dynamically, no compile time type information can be referenced in
 * this class in the form of generics. Instead, all type information is supplied only via a Class<*> object.
 */
class JsonDeserializerAdaptor(private val jsonDeserializer: JsonDeserializer<*>, val deserializingType: Class<*>)
: StdDeserializer<Any>(deserializingType) {
    /**
     * Note to maintainers. StdDeserializer<Any> requires we return an Any. The wrapper Corda deserializer returns a
     * specific type, but that is of course always a subclass of Any, so this works fine. Jackson casts the object you
     * are parsing at the point of reading, erasure prevents it from keeping track of that type, so the type being
     * returned here of Any doesn't actually affect the type you get returned at parsing time.
     */
    override fun deserialize(jsonParser: JsonParser, ctxt: DeserializationContext) =
        jsonDeserializer.deserialize(JsonNodeReaderAdaptor(jsonParser, ctxt))
}
