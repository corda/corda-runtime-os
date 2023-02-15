@file:JvmName("MarshallingUtils")
package net.corda.v5.application.marshalling

/**
 * Parse input strings to strongly typed objects.
 *
 * This method will throw an exception if the provided string does not conform to the expected format of the service.
 *
 * @param input The input string to parse.
 *
 * @return An instance of the required type containing the input data.
 */
inline fun <reified T> MarshallingService.parse(input: String): T {
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
