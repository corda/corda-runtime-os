package net.corda.v5.base.types

/**
 * Interface for supporting Map<String, String> structure.
 * Has the required functions for converting and parsing the String values to Objects.
 *
 * The layered property map provides simple conversions to a possibly complex objects which can use several keys in
 * dot-notation. Take as an example of the map:
 *
 * - "corda.name" to "CN=me, O=R3, L=Dublin, C=IE",
 * - "corda.sessionKey" to "-----BEGIN PUBLIC KEY-----Base64–encoded public key-----END PUBLIC KEY-----",
 * - "corda.endpoints.0.url" to "localhost",
 * - "corda.endpoints.0.protocolVersion" to "1",
 * - "corda.endpoints.1.url" to "localhost",
 * - "corda.endpoints.1.protocolVersion" to "2"
 *
 * That map can be parsed into:
 * - MemberX500Name using parse("corda.name", MemberX500Name::class.java)
 * - session PublicKey using parse("corda.sessionKey", PublicKey::class.java)
 * - list of endpoints using parseList("corda.endpoints", EndpointInfo::class.java)
 *
 * Example usages:
 *
 * ```java
 * Set<Map.Entry<String, String>> entries = propertyMap.getEntries();
 * String groupId = propertyMap.parse("corda.groupId", String.class);
 * Instant modifiedTime = propertyMap.parseOrNull("corda.modifiedTime", Instant.class);
 * Set<String> additionalInformation = propertyMap.parseSet("additional.names", String.class);
 * List<EndpointInfo> endpoints = propertyMap.parseList("corda.endpoints", EndpointInfo.class);
 * ```
 *
 * ```kotlin
 * val entries = propertyMap.entries
 * val groupId = propertyMap.parse("corda.groupId", String::class.java)
 * val modifiedTime = propertyMap.parseOrNull("corda.modifiedTime", Instant::class.java)
 * val additionalInformation = propertyMap.parseSet("additional.names", String::class.java)
 * val endpoints = propertyMap.parseList("corda.endpoints", EndpointInfo::class.java)
 * ```
 *
 * The default implementation of the [LayeredPropertyMap] is extendable by supplying implementations of custom
 * converters using OSGi. Out of box it supports conversion to simple types like Int, Boolean,
 * as well as [MemberX500Name].
 *
 * @property entries Returns [Set] of all entries in the underlying map.
 */
interface LayeredPropertyMap {
    val entries: Set<Map.Entry<String, String?>>

    /**
     * Finds the value of the given key in the entries.
     *
     * @param key Key for the entry we are looking for.
     *
     * @return The value of the given key or null if the key doesn't exist.
     */
    operator fun get(key: String): String?

    /**
     * Converts the value of the given key to the specified type.
     *
     * @param key Key for the entry we are looking for.
     * @param clazz The type of the value we want to convert to.
     *
     * @throws [IllegalArgumentException] if the [T] is not supported or the [key] is blank string.
     * @throws [ValueNotFoundException] if the key is not found or the value for the key is null.
     * @throws [ClassCastException] as the result of the conversion is cached, it'll be thrown if the second time around
     * the [T] is different from it was called for the first time.
     *
     * @return The parsed values for given type.
     */
    fun <T> parse(key: String, clazz: Class<out T>): T

    /**
     * Converts the value of the given key to the specified type or returns null if the key is not found or the value
     * itself is null.
     *
     * @param key Key for the entry we are looking for.
     * @param clazz The type of the value we want to convert to.
     *
     * @throws [IllegalArgumentException] if the [T] is not supported or the [key] is blank string.
     * @throws [ClassCastException] as the result of the conversion is cached, it'll be thrown if the second time around
     * the [T] is different from it was called for the first time.
     *
     * @return The parsed values for given type or null if the key doesn't exist.
     * */
    fun <T> parseOrNull(key: String, clazz: Class<out T>): T?

    /**
     * Converts several items with the given prefix to the list.
     *
     * @param itemKeyPrefix Prefix of the key for the entry we are looking for.
     * @param clazz The type of the elements in the list.
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
     *  @return A parsed list of elements for given type.
     */
    fun <T> parseList(itemKeyPrefix: String, clazz: Class<out T>): List<T>

    /**
     * Converts several items with the given prefix to [Set].
     *
     * @param itemKeyPrefix Prefix of the key for the entry we are looking for.
     * @param clazz The type of the elements in the set.
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
    fun <T> parseSet(itemKeyPrefix: String, clazz: Class<out T>): Set<T>
}
