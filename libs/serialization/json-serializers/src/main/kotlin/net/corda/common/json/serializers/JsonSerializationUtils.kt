package net.corda.common.json.serializers

import java.lang.IllegalArgumentException
import java.lang.reflect.ParameterizedType

/**
 * Return the class name of the target of a JsonSerializer or JsonDeserializer.
 *
 * @param jsonSerializer An instance of the interface to check the type for. Only pass the interface type directly, do
 * not attempt to pass the concrete derived class type.
 *
 * @throws IllegalArgumentException if type cannot be found.
 */
inline fun <reified T : Any> serializableClassNameFromJsonSerializer(jsonSerializer: T): String {
    val clazz = jsonSerializer::class.java
    val types = clazz.genericInterfaces
        .filterIsInstance<ParameterizedType>()
        .filter { it.rawType === T::class.java }
        .flatMap { it.actualTypeArguments.asList() }
    if (types.size != 1) {
        throw IllegalArgumentException("Unable to determine serializing type from ${jsonSerializer::class.java.canonicalName}")
    }

    return types.first().typeName
}
