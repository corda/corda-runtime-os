package net.corda.v5.base.types

/**
 * Interface for supporting Map<String, String> structure.
 * Has the required functions for converting and parsing the String values to Objects.
 *
 * The layered property map provides simple conversions to a possibly complex objects which can use several keys in
 * dot-notation. Take as an example of the map:
 *
 * "corda.name" to "CN=me, O=R3, L=Dublin, C=Ireland",
 * "corda.sessionKey" to "ABCDEF...",
 * "corda.endpoints.0.url" to "localhost",
 * "corda.endpoints.0.protocolVersion" to "1",
 * "corda.endpoints.1.url" to "localhost",
 * "corda.endpoints.1.protocolVersion" to "2"
 *
 * That map can be parsed into:
 * - MemberX500Name using parse("corda.name", MemberX500Name::class.java)
 * - session PublicKey using parse("corda.sessionKey", PublicKey::class.java)
 * - list of endpoints using parseList("corda.endpoints", EndpointInfo::class.java)
 *
 * The default implementation of the [LayeredPropertyMap] is extendable by supplying implementations of custom
 * converters using OSGi. Out of box it supports conversion to simple types like Int, Boolean,
 * as well as MemberX500Name.
 */
interface LayeredPropertyMap {

    /**
     * Returns the value of the given key or null if the key doesn't exist.
     */
    operator fun get(key: String): String?

    /**
     * Returns [Set] of all entries in the underlying map.
     */
    val entries: Set<Map.Entry<String, String?>>

    /**
     * Converts the value of the given key to the specified type.
     *
     * @throws [IllegalArgumentException] if the [T] is not supported or the [key] is blank string.
     * @throws [ValueNotFoundException] if the key is not found or the value for the key is null.
     * @throws [ClassCastException] as the result of the conversion is cached, it'll be thrown if the second time around
     * the [T] is different from it was called for the first time.
     */
    fun <T> parse(key: String, clazz: Class<out T>): T

    /**
     * Converts the value of the given key to the specified type or returns null if the key is not found or the value
     * itself is null.
     *
     * @throws [IllegalArgumentException] if the [T] is not supported or the [key] is blank string.
     * @throws [ClassCastException] as the result of the conversion is cached, it'll be thrown if the second time around
     * the [T] is different from it was called for the first time.
     * */
    fun <T> parseOrNull(key: String, clazz: Class<out T>): T?

    /**
     * Converts several items with the given prefix to the list.
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
     */
    fun <T> parseList(itemKeyPrefix: String, clazz: Class<out T>): List<T>

    /**
     * Converts several items with the given prefix to [Set].
     *
     * @throws [IllegalArgumentException] if the [T] is not supported or the [itemKeyPrefix] is blank string.
     * @throws [ValueNotFoundException] if one of the list values is null.
     * @throws [ClassCastException] as the result of the conversion is cached, it'll be thrown if the second time around
     * the [T] is different from it was called for the first time.
     *
     * Here is an example of what a set will look like
     * (the [itemKeyPrefix] has to be "corda.identityKeyHashes" or "corda.identityKeyHashes."):
     *  corda.identityKeyHashes.1 = <hash value of identity key 1>
     *  corda.identityKeyHashes.2 = <hash value of identity key 2>
     *  corda.identityKeyHashes.3 = <hash value of identity key 3>
     */
    fun <T> parseSet(itemKeyPrefix: String, clazz: Class<out T>): Set<T>
}
