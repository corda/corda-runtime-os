package net.corda.v5.application.serialization

import net.corda.v5.application.injection.CordaFlowInjectable
import net.corda.v5.application.injection.CordaServiceInjectable
import net.corda.v5.base.annotations.DoNotImplement

/**
 * An optional service CorDapps and other services may use to marshall arbitrary content in and out of JSON format using standard/approved
 * mappers.
 */
@DoNotImplement
interface JsonMarshallingService : CordaServiceInjectable, CordaFlowInjectable {

    /**
     * Serialize the [input] object into JSON.
     *
     * @param input The object to serialize into JSON.
     *
     * @return The JSON representation of [input].
     */
    fun formatJson(input: Any): String

    /**
     * Deserializes the [input] JSON into an instance of [T].
     *
     * @param input The JSON to deserialize.
     * @param clazz The [Class] type to deserialize into.
     *
     * @return A new instance of [T].
     */
    fun <T> parseJson(input: String, clazz: Class<T>): T

    /**
     * Deserializes the [input] JSON into a list of instances of [T].
     *
     * @param input The JSON to deserialize.
     * @param clazz The [Class] type to deserialize into.
     *
     * @return A new list of [T].
     */
    fun <T> parseJsonList(input: String, clazz: Class<T>): List<T>
}

/**
 * Deserializes the [input] JSON into an instance of [T].
 *
 * @param input The JSON to deserialize.
 *
 * @return A new instance of [T].
 */
inline fun <reified T> JsonMarshallingService.parseJson(input: String): T {
    return parseJson(input, T::class.java)
}

/**
 * Deserializes the [input] JSON into a list of instances of [T].
 *
 * @param input The JSON to deserialize.
 *
 * @return A new list of [T].
 */
inline fun <reified T> JsonMarshallingService.parseJsonList(input: String): List<T> {
    return parseJsonList(input, T::class.java)
}