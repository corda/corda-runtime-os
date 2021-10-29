package net.corda.v5.cipher.suite.config

/**
 * Defines the crypto library configuration, such as RPC, cache, etc.,
 * most values are internal to the crypto library implementation.
 */
interface CryptoLibraryConfig : Map<String, Any?>

/**
 * Similar to typesafe's hasPath where it returns true if the key is present, and it's not null
 */
fun Map<String, Any?>.hasPath(key: String) : Boolean = get(key) != null
