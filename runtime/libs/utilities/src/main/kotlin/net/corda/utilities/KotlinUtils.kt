@file:JvmName("KotlinUtils")
package net.corda.utilities

import net.corda.v5.base.types.LayeredPropertyMap
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration

/** Like the + operator but throws [ArithmeticException] in case of integer overflow. */
infix fun Int.exactAdd(b: Int): Int = Math.addExact(this, b)

/** Like the + operator but throws [ArithmeticException] in case of integer overflow. */
infix fun Long.exactAdd(b: Long): Long = Math.addExact(this, b)

/** Returns the logger used for detailed logging. */
fun detailedLogger(): Logger = LoggerFactory.getLogger("DetailedInfo")

/** Log a TRACE level message produced by evaluating the given lambda, but only if TRACE logging is enabled. */
inline fun Logger.trace(msg: () -> String) {
    if (isTraceEnabled) trace(msg())
}

/** Log a DEBUG level message produced by evaluating the given lambda, but only if DEBUG logging is enabled. */
inline fun Logger.debug(msg: () -> String) {
    if (isDebugEnabled) debug(msg())
}

/**
 * Extension method for easier construction of [Duration]s in terms of integer days: `val twoDays = 2.days`.
 * @see Duration.ofDays
 */
val Int.days: Duration get() = Duration.ofDays(toLong())

/**
 * Extension method for easier construction of [Duration]s in terms of integer hours: `val twoHours = 2.hours`.
 * @see Duration.ofHours
 */
val Int.hours: Duration get() = Duration.ofHours(toLong())

/**
 * Extension method for easier construction of [Duration]s in terms of integer minutes: `val twoMinutes = 2.minutes`.
 * @see Duration.ofMinutes
 */
val Int.minutes: Duration get() = Duration.ofMinutes(toLong())

/**
 * Extension method for easier construction of [Duration]s in terms of integer seconds: `val twoSeconds = 2.seconds`.
 * @see Duration.ofSeconds
 */
val Int.seconds: Duration get() = Duration.ofSeconds(toLong())

/**
 * Extension method for easier construction of [Duration]s in terms of integer milliseconds: `val twoMillis = 2.millis`.
 * @see Duration.ofMillis
 */
val Int.millis: Duration get() = Duration.ofMillis(toLong())

/**
 * Function for reading and parsing the String values stored in the values to actual objects.
 *
 * @param key The key we are looking for in the store.
 *
 * @throws [IllegalArgumentException] if the [T] is not supported or the [key] is blank string.
 * @throws [ValueNotFoundException] if the key is not found or the value for the key is null.
 * @throws [ClassCastException] as the result of the conversion is cached, it'll be thrown if the second time around
 * the [T] is different from it was called for the first time.
 *
 * @return The parsed values for given type.
 */
inline fun <reified T> LayeredPropertyMap.parse(key: String): T {
    return parse(key, T::class.java)
}

/**
 * Function for reading and parsing the String values stored in the values to actual objects or return null.
 *
 * @param key The key we are looking for in the store.
 *
 * @throws [IllegalArgumentException] if the [T] is not supported or the [key] is blank string.
 * @throws [ClassCastException] as the result of the conversion is cached, it'll be thrown if the second time around
 * the [T] is different from it was called for the first time.
 *
 * @return The parsed values for given type or null if the key doesn't exist.
 */
inline fun <reified T> LayeredPropertyMap.parseOrNull(key: String): T? {
    return parseOrNull(key, T::class.java)
}

/**
 * Function for reading and parsing the String values stored in the values to an actual list of objects.
 *
 * @param itemKeyPrefix The key prefix we are looking for in the store.
 *
 * @throws [IllegalArgumentException] if the [T] is not supported or the [itemKeyPrefix] is blank string.
 * @throws [ValueNotFoundException] if one of the list values is null.
 * @throws [ClassCastException] as the result of the conversion is cached, it'll be thrown if the second time around
 * the [T] is different from it was called for the first time.

 * Here is an example how a list will look like
 * (the [itemKeyPrefix] have to be "corda.endpoints" or "corda.endpoints."):
 *  corda.endpoints.1.url = localhost
 *  corda.endpoints.1.protocolVersion = 1
 *  corda.endpoints.2.url = localhost
 *  corda.endpoints.2.protocolVersion = 1
 *  corda.endpoints.3.url = localhost
 *  corda.endpoints.3.protocolVersion = 1
 *
 * @return A parsed list of elements for given type.
 */
inline fun <reified T> LayeredPropertyMap.parseList(itemKeyPrefix: String): List<T> {
    return parseList(itemKeyPrefix, T::class.java)
}

/**
 * Function for reading and parsing the String values stored in the values to an actual set of objects.
 *
 * @param itemKeyPrefix The key prefix we are looking for in the store.
 *
 * @throws [IllegalArgumentException] if the [T] is not supported or the [itemKeyPrefix] is blank string.
 * @throws [ValueNotFoundException] if one of the list values is null.
 * @throws [ClassCastException] as the result of the conversion is cached, it'll be thrown if the second time around
 * the [T] is different from it was called for the first time.
 *
 * Here is an example of what a set will look like
 * (the [itemKeyPrefix] has to be "corda.ledgerKeyHashes" or "corda.ledgerKeyHashes."):
 *  corda.ledgerKeyHashes.1 = <hash value of ledger key 1>
 *  corda.ledgerKeyHashes.2 = <hash value of ledger key 2>
 *  corda.ledgerKeyHashes.3 = <hash value of ledger key 3>
 *
 * @return A parsed set of elements for given type.
 */
inline fun <reified T> LayeredPropertyMap.parseSet(itemKeyPrefix: String): Set<T> {
    return parseSet(itemKeyPrefix, T::class.java)
}
