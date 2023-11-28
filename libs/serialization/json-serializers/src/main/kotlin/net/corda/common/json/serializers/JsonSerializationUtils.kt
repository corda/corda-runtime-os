package net.corda.common.json.serializers

import java.lang.IllegalArgumentException
import java.lang.reflect.ParameterizedType

/**
 * Return the class of the target of a JsonSerializer or JsonDeserializer.
 *
 * @param jsonSerializer An instance of the interface to check the type for. Only pass the interface type directly, do
 * not attempt to pass the concrete derived class type.
 *
 * @throws IllegalArgumentException if type cannot be found.
 */
inline fun <reified T : Any> serializableClassFromJsonSerializer(jsonSerializer: T): Class<*> {
    return serializableClassFromJsonSerializer(jsonSerializer, T::class.java)
}

fun <T : Any> serializableClassFromJsonSerializer(jsonSerializer: T, jsonSerializerClass: Class<T>): Class<*> {
    val clazz = jsonSerializer::class.java
    val types = clazz.genericInterfaces
        .filterIsInstance<ParameterizedType>()
        .filter { it.rawType === jsonSerializerClass }
        .flatMap { it.actualTypeArguments.asList() }
    if (types.size != 1) {
        throw IllegalArgumentException("Unable to determine serializing type from ${clazz.canonicalName}")
    }

    return when (val underlying = types.first()) {
        is Class<*> -> underlying
        is ParameterizedType -> underlying.rawType as Class<*>
        else -> throw TypeNotPresentException(underlying.typeName, null)
    }
}
