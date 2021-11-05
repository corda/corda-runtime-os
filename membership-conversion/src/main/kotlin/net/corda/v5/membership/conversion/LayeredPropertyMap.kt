package net.corda.v5.membership.conversion

import net.corda.v5.base.exceptions.CordaRuntimeException

/**
 * Interface for supporting Map<String, String> structure.
 * Has the required functions for converting and parsing the String values to Objects.
 */
interface LayeredPropertyMap {
    operator fun get(key: String): String?

    val entries: Set<Map.Entry<String, String?>>

    fun <T> parse(key: String,
                  clazz: Class<out T>): T

    fun <T> parseOrNull(key: String,
                        clazz: Class<out T>): T?

    fun <T> parseList(itemKeyPrefix: String,
                      clazz: Class<out T>): List<T>
}

/**
 * Function for reading and parsing the String values stored in the values to actual objects.
 *
 * @param key The key we are looking for in the store.
 */
inline fun <reified T> LayeredPropertyMap.parse(key: String): T {
    return parse(key, T::class.java)
}

/**
 * Function for reading and parsing the String values stored in the values to actual objects or return null.
 *
 * @param key The key we are looking for in the store.
 */
inline fun <reified T> LayeredPropertyMap.parseOrNull(key: String): T? {
    return parseOrNull(key, T::class.java)
}

/**
 * Function for reading and parsing the String values stored in the values to an actual list of objects.
 *
 * @param itemKeyPrefix The key prefix we are looking for in the store.
 */
inline fun <reified T> LayeredPropertyMap.parseList(itemKeyPrefix: String): List<T> {
    return parseList(itemKeyPrefix, T::class.java)
}

/**
 * Exception, being thrown if a value for a specific key cannot be found in the [LayeredPropertyMap].
 */
class ValueNotFoundException(message: String?) : CordaRuntimeException(message)