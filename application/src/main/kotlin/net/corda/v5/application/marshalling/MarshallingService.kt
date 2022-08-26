@file:JvmName("MarshallingUtils")
package net.corda.v5.application.marshalling

import net.corda.v5.base.annotations.DoNotImplement

/**
 * Handles conversion of string data to and from strongly typed objects.
 *
 * Corda provides a number of marshalling services for converting between string data in different formats. Users should
 * not ask for a [MarshallingService] directly but should instead use one of the specialized services that declare
 * what format to work with (e.g. [JsonMarshallingService] for working with JSON data).
 */
@DoNotImplement
interface MarshallingService {

    /**
     * Format the input data into the service's output format.
     *
     * @param data The object to convert on input
     * @return String representation of the data formatted according to the provided service
     */
    fun format(data: Any): String

    /**
     * Parse input strings to strongly typed objects.
     *
     * This method will throw an exception if the provided string does not conform to the expected format of the service.
     *
     * @param input The input string to parse
     * @param clazz The type to try and parse the data into
     * @return An instance of the required type containing the input data
     */
    fun <T> parse(input: String, clazz: Class<T>) : T

    /**
     * Deserializes the [input] into a list of instances of [T].
     *
     * @param input The input string to parse.
     * @param clazz The [Class] type to parse into.
     *
     * @return A new list of [T].
     */
    fun <T> parseList(input: String, clazz: Class<T>): List<T>
}

/**
 * Parse input strings to strongly typed objects.
 *
 * This method will throw an exception if the provided string does not conform to the expected format of the service.
 *
 * @param input The input string to parse
 * @return An instance of the required type containing the input data
 */
inline fun <reified T> MarshallingService.parse(input: String) : T {
    return this.parse(input, T::class.java)
}

/**
 * Deserializes the [input] into a list of instances of [T].
 *
 * @param input The input string to deserialize.
 *
 * @return A new list of [T].
 */
inline fun <reified T> MarshallingService.parseList(input: String): List<T> {
    return parseList(input, T::class.java)
}