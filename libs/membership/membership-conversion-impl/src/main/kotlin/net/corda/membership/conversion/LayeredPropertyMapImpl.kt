package net.corda.membership.conversion

import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.v5.membership.conversion.ConversionContext
import net.corda.v5.membership.conversion.LayeredPropertyMap
import net.corda.v5.membership.conversion.PropertyConverter
import net.corda.v5.membership.conversion.ValueNotFoundException
import java.nio.ByteBuffer
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
        val cached = cache[key]
        if(cached != null) {
            // needed because the simple approach using as? T does not do the actual casting without value assignment,
            // hence it won't throw the exception here
            if(!clazz.isAssignableFrom(cached::class.java)) {
                throw ClassCastException("Casting failed for $key.")
            }
            return cached as T
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
        val simplePrefix = simplifySearchKeyPrefix(itemKeyPrefix)
        val cached = cache[simplePrefix]
        if(cached != null) {
            // needed because the simple approach using as? T does not do the actual casting without value assignment,
            // hence it won't throw the exception here
            val converted = cached as List<T>
            converted.firstOrNull()?.let {
                if(!clazz.isAssignableFrom(it::class.java)) {
                    throw ClassCastException("Casting failed for $simplePrefix prefix.")
                }
            }
            return converted
        }

        // normalise prefix, add "." at the end to make processing easier and make usage foolproof
        val normalisedPrefix = normaliseSearchKeyPrefix(itemKeyPrefix)

        // 2. Check the matching elements in entries
        val matchingEntries = entries.filter { it.key.startsWith(normalisedPrefix) }

        // 3. If no matching elements in entries, throw exception
        if (matchingEntries.isEmpty()) throw ValueNotFoundException("There is no value for '$itemKeyPrefix' prefix.")

        // 4. Checking that the structure has numbers in it, if not then throwing an exception
        val entryList = matchingEntries.takeIf {
            it.all { it.key.removePrefix(normalisedPrefix).first().isDigit() }
        } ?: throw IllegalArgumentException("Prefix is invalid, only number is accepted after prefix.")

        // 5. grouping by the indexes
        // then convert it to a map one by one and pass it to the converter
        // 1 -> [corda.endpoints.1.url=localhost, corda.endpoints.1.protocolVersion=1]
        // 2 -> [corda.endpoints.2.url=localhost, corda.endpoints.2.protocolVersion=1]
        // 1 -> [corda.identityKeys.1=ABC]
        val result = entryList.groupBy {
            it.key.removePrefix(normalisedPrefix).first()
        }.map { groupedEntry ->
            groupedEntry.key to (groupedEntry.value.map { it.key to it.value }).toMap()
        }.map {
            // instead of the whole context, we are just passing a pre-processed properties to the converter one by one
            converter.convert(ConversionContext(LayeredPropertyMapImpl(it.second.toSortedMap(), converter), this::class.java, normalisedPrefix), clazz)
                ?: throw IllegalStateException("Error while converting $itemKeyPrefix prefix.")
        }

        // 6. put result into cache
        cache[simplePrefix] = result
        return result
    }

    /**
     * Function for reading and parsing the String values to actual objects or null.
     */
    @Suppress("UNCHECKED_CAST")
    override fun <T> parseOrNull(key: String, clazz: Class<out T>): T? {
        // 1. Check if value already in cache, if yes, return that value (caching unfortunately won't work for nulls)
        val cached = cache[key]
        if(cached != null) {
            if(!clazz.isAssignableFrom(cached::class.java)) {
                throw ClassCastException("Casting failed for $key.")
            }
            return cached as T
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
 * Extension function for converting the content of [KeyValueStore] to a list of [KeyValuePair].
 * This conversion is required, because of the avro serialization done on the P2P layer.
 */
fun LayeredPropertyMap.toKeyValuePairList(): ByteBuffer = KeyValuePairList(entries.map { KeyValuePair(it.key, it.value) }).toByteBuffer()