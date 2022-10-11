package net.corda.common.json.serializers

import net.corda.v5.application.marshalling.json.JsonDeserializer
import net.corda.v5.application.marshalling.json.JsonSerializer

/**
 * An interface which allows custom Json serializers and deserializers to be set.
 */
interface SerializationCustomizer {
    /**
     * Sets a serializer for a certain class type. If a serializer already exists for this class type, does nothing.
     *
     * @param serializer The custom serializer
     * @param clazz The class it applies to
     *
     * @return true if serializer was added, otherwise false
     */
    fun <T> setSerializer(serializer: JsonSerializer<T>, clazz: Class<T>): Boolean

    /**
     * Sets a deserializer for a certain class type. If a serializer already exists for this class type, does nothing.
     *
     * @param deserializer The custom deserializer
     * @param clazz The class it applies to
     *
     * @return true if deserializer was added, otherwise false
     */
    fun <T> setDeserializer(deserializer: JsonDeserializer<T>, clazz: Class<T>): Boolean
}
