package net.corda.layeredpropertymap.impl

import net.corda.layeredpropertymap.ConversionContext
import net.corda.v5.base.exceptions.ValueNotFoundException
import net.corda.v5.base.types.LayeredPropertyMap
import java.util.concurrent.ConcurrentHashMap

class LayeredPropertyMapImpl(
    private val properties: Map<String, String?>,
    private val converter: PropertyConverter
) : LayeredPropertyMap {

    companion object {
        private val indexedPrefixComparator = IndexedPrefixComparator()
    }

    private class CachedValue(val value: Any?)

    private val cache = ConcurrentHashMap<String, CachedValue>()

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
        if (cached?.value != null) {
            // needed because the simple approach using as? T does not do the actual casting without value assignment,
            // hence it won't throw the exception here
            if (!clazz.isAssignableFrom(cached.value::class.java)) {
                throw ClassCastException("Casting failed for $key.")
            }
            return cached.value as T
        }

        // 2. Convert the string value using the provided or built-in converter or our built-in primitive converter
        // if not provided
        val convertedValue = converter.convert(ConversionContext(this, this::class.java, key), clazz)
            ?: throw ValueNotFoundException("There is no value for '$key' key or it's null.")

        // 3. Assign the converted value in the cache and return it
        cache[key] = CachedValue(convertedValue)
        return convertedValue
    }

    /**
     * Function for reading and parsing the String values to actual objects or null.
     */
    @Suppress("UNCHECKED_CAST")
    override fun <T> parseOrNull(key: String, clazz: Class<out T>): T? {
        // 1. Check if value already in cache, if yes, return that value (caching unfortunately won't work for nulls)
        val cached = cache[key]
        if (cached?.value != null) {
            if (!clazz.isAssignableFrom(cached.value::class.java)) {
                throw ClassCastException("Casting failed for $key.")
            }
            return cached.value as T
        }

        // 2. Convert the value with the converter (provided or builtin), if no converter is found, use our
        // default primitive converter
        val convertedValue = converter.convert(ConversionContext(this, this::class.java, key), clazz)

        // 3. Assign converted value and return it
        cache[key] = CachedValue(convertedValue)
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
        // normalise prefix, add "." at the end to make processing easier and make usage foolproof
        val normalisedPrefix = normaliseListSearchKeyPrefix(itemKeyPrefix)
        // 1. Check if list already in cache, if yes, return that list
        val cached = cache[normalisedPrefix]
        if (cached?.value != null) {
            // needed because the simple approach using as? T does not do the actual casting without value assignment,
            // hence it won't throw the exception here
            val converted = cached.value as List<T>
            converted.firstOrNull()?.let {
                if (!clazz.isAssignableFrom(it::class.java)) {
                    throw ClassCastException("Casting failed for $normalisedPrefix prefix.")
                }
            }
            return converted
        }

        // 2. Check the matching elements in entries
        val matchingEntries = entries.filter { it.key.startsWith(normalisedPrefix) }

        // 3. If no matching elements in entries, return empty list
        if (matchingEntries.isEmpty()) return emptyList()

        // 4. Checking that the structure has numbers in it, if not then throwing an exception
        val entryList = matchingEntries.takeIf {
            it.all { pair -> pair.key.removePrefix(normalisedPrefix).first().isDigit() }
        } ?: throw IllegalArgumentException("Prefix is invalid, only number is accepted after prefix.")

        // 5. grouping by the indexes
        // then convert it to a map one by one and pass it to the converter
        // 1 -> [corda.endpoints.1.url=localhost, corda.endpoints.1.protocolVersion=1]
        // 2 -> [corda.endpoints.2.url=localhost, corda.endpoints.2.protocolVersion=1]
        // 1 -> [corda.identityKeys.1=ABC]
        val result = entryList.groupBy {
            getIndexedPrefix(it.key, normalisedPrefix)
        }.toSortedMap(indexedPrefixComparator).map { groupedEntry ->
            groupedEntry.key to (groupedEntry.value.map { it.key to it.value }).toLinkedHashMap()
        }.map {
            // instead of the whole context, we are just passing a pre-processed properties to the converter one by one
            val map = it.second.map { item ->
                item.key.removePrefix(it.first.second) to item.value
            }.toLinkedHashMap()
            val itemContext =  ConversionContext(LayeredPropertyMapImpl(map, converter), this::class.java, "")
            converter.convert(itemContext, clazz)
                ?: throw ValueNotFoundException("Error while converting $itemKeyPrefix prefix.")
        }

        // 6. put result into cache
        cache[normalisedPrefix] = CachedValue(result)
        return result
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

    private fun normaliseListSearchKeyPrefix(itemKeyPrefix: String): String {
        if (itemKeyPrefix.endsWith(".")) {
            return itemKeyPrefix
        }
        return "$itemKeyPrefix."
    }

    private fun getIndexedPrefix(key: String, normalisedPrefix: String): Pair<Int, String> {
        val dotPos = key.indexOf(".", normalisedPrefix.length)
        return if(dotPos < 0) {
            key.substring(normalisedPrefix.length).toInt() to key
        } else {
            key.substring(normalisedPrefix.length, dotPos).toInt() to key.substring(0, dotPos + 1)
        }
    }

    private fun <K, V> List<Pair<K, V>>.toLinkedHashMap(): LinkedHashMap<K, V> {
        val map = LinkedHashMap<K, V>()
        forEach {
            map[it.first] = it.second
        }
        return map
    }

    private class IndexedPrefixComparator : Comparator<Pair<Int, String>> {
        override fun compare(o1: Pair<Int, String>, o2: Pair<Int, String>): Int {
            return o1.first.compareTo(o2.first)
        }
    }
}
