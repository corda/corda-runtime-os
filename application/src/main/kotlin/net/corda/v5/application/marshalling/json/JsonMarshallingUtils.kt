@file:JvmName("JsonMarshallingUtils")
package net.corda.v5.application.marshalling.json

import net.corda.v5.application.marshalling.JsonMarshallingService

/**
 * Parse this [JsonNodeReader] to strongly typed objects. Will deserialize using default deserializers or any custom
 * Json deserializers registered. This method can be used if during custom deserialization of one class type, the
 * deserializer expects a field's value to contain a Json object which can be deserialized to another class type which
 * is already known to either be default deserializable, or for which other custom deserializers are registered.
 * It is the equivalent of calling the [JsonMarshallingService] parse method on a Json string representation of this
 * node.
 *
 * @return An instance of the required type or null if this node does not represent a Json serialized version of
 * that type.
 */
inline fun <reified T> JsonNodeReader.parse(): T? = this.parse(T::class.java)
