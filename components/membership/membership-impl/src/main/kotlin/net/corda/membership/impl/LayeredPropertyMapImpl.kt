package net.corda.membership.impl

import net.corda.data.WireKeyValuePair
import net.corda.v5.membership.converter.ConversionContext
import net.corda.v5.membership.converter.PropertyConverter
import net.corda.v5.membership.properties.LayeredPropertyMap
import net.corda.v5.membership.properties.ValueNotFoundException
import java.lang.ClassCastException
import java.util.SortedMap
import java.util.concurrent.ConcurrentHashMap

@Suppress("MaxLineLength")
open class LayeredPropertyMapImpl(
    private val properties: SortedMap<String, String?>,
    private val converter: PropertyConverter
): LayeredPropertyMap {

    private val cache = ConcurrentHashMap<String, Any?>()

    override operator fun get(key: String): String? = properties[key]

    @Transient
    override val entries: Set<Map.Entry<String, String?>> = properties.entries

    /**
     * Function for reading and parsing the String values to actual objects.
     */
    @Suppress("UNCHECKED_CAST")
    override fun <T> parse(key: String, clazz: Class<out T>): T {

        // 1. Check if value already is in our cache, if yes, return that value
        cache[key]?.let {
            return it as? T  ?: throw ClassCastException("Casting failed for $it at $key.")
        }

        // 2. Check if value exists in entries, if not, throw exception since impossible to convert
        // using startsWith in order to find entries for "corda.party" key too (corda.party.name and corda.party.owningKey)
        val values: List<Pair<String, String?>> = properties.filter {
            it.key.startsWith(key)
        }.map { it.key to it.value }

        if (values.isEmpty()) throw ValueNotFoundException("There is no value for '$key' key.")

        // 3. Convert the string value using the provided or built-in converter or our built-in primitive converter if not provided
        val convertedValue = converter.convert(
            ConversionContext(this, this::class.java, key),
            clazz
        ) ?: throw IllegalStateException("Converted value cannot be null.")

        // 4. Assign the converted value in the cache and return it
        cache[key] = convertedValue
        return convertedValue
    }

    /**
     * Function for reading and parsing the list of String values to a list of actual objects.
     *
     * Here is an example how a list will look like in the MemberInfo:
     *  corda.endpoints.1.url = localhost
     *  corda.endpoints.1.protocolVersion = 1
     *  corda.endpoints.2.url = localhost
     *  corda.endpoints.2.protocolVersion = 1
     *  corda.endpoints.3.url = localhost
     *  corda.endpoints.3.protocolVersion = 1
     */
    @Suppress("UNCHECKED_CAST")
    override fun <T> parseList(
        itemKeyPrefix: String,
        clazz: Class<out T>
    ): List<T> {
        // 1. Check if list already in cache, if yes, return that list
        // normalise prefix, add "." at the end to make processing easier and make usage foolproof
        val normalisedPrefix = normaliseSearchKeyPrefix(itemKeyPrefix)
        cache[normalisedPrefix]?.let {
            return it as? List<T> ?: throw ClassCastException("Casting failed for $it at $normalisedPrefix prefix.")
        }

        // 2. Check the matching elements in entries
        val matchingEntries = entries.filter { it.key.startsWith(normalisedPrefix) }

        // 3. If no matching elements in entries, return emptylist since it's impossible to cast if not exist
        if (matchingEntries.isEmpty()) {
            return emptyList()
        }

        // creating the following transformation: corda.endpoints.1.url -> 1.url
        // 1.url = localhost
        // 1.protocolVersion = 1
        // 2.url = localhost
        // 2.protocolVersion = 1
        // 3.url = localhost
        // 3.protocolVersion = 1
        // in case of identityKeys: corda.identityKeys.1 -> 1
        // 1 = ABC
        val strippedEntries = matchingEntries.map {
            it.key.removePrefix(normalisedPrefix) to it.value
        }.takeIf {
            it.all { it.first[0].isDigit() }
        } ?: throw IllegalArgumentException("Prefix is invalid, only number is accepted after prefix.")

        // grouping by the indexes and stripping off them
        // then convert it to a map and pass it to the converter
        val result = strippedEntries.groupBy {
            // 1 -> [1.url=localhost, 1.protocolVersion=1]
            // 2 -> [2.url=localhost, 2.protocolVersion=1]
            // 1 -> [1=ABC]
            it.first[0].toInt()
        }.map { groupedEntry ->
            // 1 -> [url=localhost,protocolVersion=1]
            // 2 -> [url=localhost,protocolVersion=1]
            // 1 -> [1=ABC]
            groupedEntry.key to (groupedEntry.value.map { it.first.split(".").last() to it.second }).toMap()
        }.map {
            // instead of the whole context, we are just passing a pre-processed properties to the converter
            // containing only the relevant parts to us
            // example, the context here is: url = localhost, protocolVersion=1
            converter.convert(ConversionContext(LayeredPropertyMapImpl(it.second.toSortedMap(), converter), this::class.java, itemKeyPrefix), clazz)
                ?: throw IllegalStateException("Error while converting $itemKeyPrefix prefix.")
        }
        cache[normalisedPrefix] = result
        return result
    }

    /**
     * Function for reading and parsing the String values to actual objects or null.
     */
    @Suppress("UNCHECKED_CAST")
    override fun <T> parseOrNull(key: String, clazz: Class<out T>): T? {

        // 1. Check if value already in cache, if yes, return that value
        val cachedValue = cache[key]
        cachedValue?.let {
            return cachedValue as T?
        }

        // 2. Check if value present in entries, if not or the value is null then return null
        get(key) ?: return null

        // 3. Convert the value with the converter (provided or builtin), if no converter is found, use our default primitive converter
        val convertedValue = converter.convert(
            ConversionContext(this, this::class.java, key),
            clazz
        )

        // 4. Assign converted value and return it
        cache[key] = convertedValue
        return convertedValue
    }

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is LayeredPropertyMapImpl) return false
        if (this === other) return true
        return properties == other.properties && entries == other.entries
    }

    override fun hashCode(): Int {
        var result = properties.hashCode()
        result = 31 * result + entries.hashCode()
        return result
    }

    private fun normaliseSearchKeyPrefix(itemKeyPrefix: String): String {
        if (itemKeyPrefix.endsWith(".")) {
            return itemKeyPrefix
        }
        return "$itemKeyPrefix."
    }
}

/**
 * Parser for objects.
 *
 * @param key The key we are looking for in the store.
 */
inline fun <reified T> LayeredPropertyMap.parse(key: String): T {
    return parse(key, T::class.java)
}

/**
 * Parser for objects that can return null values.
 *
 * @param key The key we are looking for in the store.
 */
inline fun <reified T> LayeredPropertyMap.parseOrNull(key: String): T? {
    return parseOrNull(key, T::class.java)
}

/**
 * Parser for list of objects.
 *
 * @param itemKeyPrefix The key prefix we are looking for in the store.
 */
inline fun <reified T> LayeredPropertyMap.parseList(itemKeyPrefix: String): List<T> {
    return parseList(itemKeyPrefix, T::class.java)
}

/**
 * Extension function for converting the content of [KeyValueStore] to a list of [WireKeyValuePair].
 * This conversion is required, because of the avro serialization done on the P2P layer.
 */
fun LayeredPropertyMap.toWireKeyValuePairList(): List<WireKeyValuePair> = entries.map { WireKeyValuePair(it.key, it.value) }