package net.corda.membership.impl

import net.corda.data.WireKeyValuePair
import net.corda.v5.membership.identity.KeyValueStore
import net.corda.v5.membership.identity.ValueNotFoundException
import net.corda.v5.membership.identity.parser.ConversionContext
import net.corda.v5.membership.identity.parser.ObjectConverter
import java.lang.ClassCastException
import java.util.SortedMap
import java.util.concurrent.ConcurrentHashMap

@Suppress("MaxLineLength")
open class KeyValueStoreImpl(
    private val properties: SortedMap<String, String?>,
    private val converter: ObjectConverter
): KeyValueStore {

    private val cache = ConcurrentHashMap<String, Any?>()

    override operator fun get(key: String): String? = properties[key]

    @Transient
    override val keys: Set<String> = properties.keys
    @Transient
    override val entries: Set<Map.Entry<String, String?>> = properties.entries

    /**
     * Function for reading and parsing the String values to actual objects.
     */
    @Suppress("UNCHECKED_CAST")
    override fun <T> parse(key: String, clazz: Class<out T>): T {

        // 1. Check if value already is in our cache, if yes, return that value
        val cachedValue = cache[key]
        cachedValue?.let {
            return cachedValue as? T  ?: throw ClassCastException("Casting failed for $cachedValue at $key.")
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
     */
    /*
        corda.endpoints.1.url = localhost
        corda.endpoints.1.protocolVersion = 1
        corda.endpoints.2.url = localhost
        corda.endpoints.2.protocolVersion = 1
        corda.endpoints.3.url = localhost
        corda.endpoints.3.protocolVersion = 1
     */
    @Suppress("UNCHECKED_CAST")
    override fun <T> parseList(
        itemKeyPrefix: String,
        clazz: Class<out T>
    ): List<T> {
        // remove the "." at the end to make the cache keys nicer and standardized
        // 1. Check if list already in cache, if yes, return that list
        val simplePrefix = simplifySearchKeyPrefix(itemKeyPrefix)
        val tmp = cache[simplePrefix]
        if(tmp != null) {
            return tmp as? List<T> ?: throw ClassCastException("Casting failed for $tmp at $simplePrefix.")
        }

        // 2. Check the matching elements in entries
        // add "." at the end to make processing easier
        val normalisedPrefix = normaliseSearchKeyPrefix(itemKeyPrefix)
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
            converter.convert(ConversionContext(KeyValueStoreImpl(it.second.toSortedMap(), converter), this::class.java, itemKeyPrefix), clazz)
                ?: throw IllegalStateException("Error while converting $itemKeyPrefix prefix.")
        }
        cache[simplePrefix] = result
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
        if (other == null || other !is KeyValueStoreImpl) return false
        if (this === other) return true
        return properties == other.properties && keys == other.keys && entries == other.entries
    }

    override fun hashCode(): Int {
        var result = properties.hashCode()
        result = 31 * result + keys.hashCode()
        result = 31 * result + entries.hashCode()
        return result
    }

    private fun normaliseSearchKeyPrefix(itemKeyPrefix: String): String {
        if (itemKeyPrefix.endsWith(".")) {
            return itemKeyPrefix
        }
        return "$itemKeyPrefix."
    }

    private fun simplifySearchKeyPrefix(itemKeyPrefix: String): String {
        if (!itemKeyPrefix.endsWith(".")) {
            return itemKeyPrefix
        }
        return itemKeyPrefix.dropLast(1)
    }
}

/**
 * Parser for objects.
 *
 * @param key The key we are looking for in the store.
 */
inline fun <reified T> KeyValueStore.parse(key: String): T {
    return parse(key, T::class.java)
}

/**
 * Parser for objects that can return null values.
 *
 * @param key The key we are looking for in the store.
 */
inline fun <reified T> KeyValueStore.parseOrNull(key: String): T? {
    return parseOrNull(key, T::class.java)
}

/**
 * Parser for list of objects.
 *
 * @param itemKeyPrefix The key prefix we are looking for in the store.
 */
inline fun <reified T> KeyValueStore.parseList(itemKeyPrefix: String): List<T> {
    return parseList(itemKeyPrefix, T::class.java)
}

/**
 * Extension function for converting the content of [KeyValueStore] to a list of [WireKeyValuePair].
 * This conversion is required, because of the avro serialization done on the P2P layer.
 */
fun KeyValueStore.toWireKeyValuePairList(): List<WireKeyValuePair> = entries.map { WireKeyValuePair(it.key, it.value) }