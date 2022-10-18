package net.corda.common.json.serializers

import net.corda.v5.application.marshalling.json.JsonDeserializer
import net.corda.v5.application.marshalling.json.JsonSerializer

/**
 * An interface which allows custom Json serializers and deserializers to be set.
 */
interface SerializationCustomizer {
    /**
     * Sets a serializer for the class type handled by the serializer. If a serializer already exists for this class
     * type, does nothing and returns false.
     *
     * @param serializer The custom serializer
     *
     * @return true if serializer was added, otherwise false
     */
    fun setSerializer(serializer: JsonSerializer<*>): Boolean

    /**
     * Sets a deserializer for the class type handled by the deserializer. If a deserializer already exists for this
     * class type, does nothing and returns false.
     *
     * @param deserializer The custom deserializer
     *
     * @return true if deserializer was added, otherwise false
     */
    fun setDeserializer(deserializer: JsonDeserializer<*>): Boolean
}
