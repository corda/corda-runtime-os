package net.corda.layeredpropertymap.impl

import java.util.concurrent.ConcurrentHashMap
import net.corda.layeredpropertymap.ConversionContext
import net.corda.v5.base.exceptions.ValueNotFoundException
import net.corda.v5.base.types.LayeredPropertyMap

class LayeredPropertyMapImpl(
    private val properties: Map<String, String?>,
    private val converter: PropertyConverter
) : LayeredPropertyMap {

    companion object {
        private val indexedPrefixComparator = IndexedPrefixComparator()
    }

    private class CachedValue(val value: Any?)

    private val cache = ConcurrentHashMap<Pair<String, Class<*>>, CachedValue>()

    override fun get(key: String): String? = properties[key]

    override fun getEntries(): Set<Map.Entry<String, String?>> = properties.entries

    /**
     * Function for reading and parsing the String values to actual objects.
     */
    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> parse(key: String, clazz: Class<out T>): T {
        require(key.isNotBlank()) {
            "The key cannot be blank string."
        }
        return cache.computeIfAbsent(Pair(key, clazz)) {
            CachedValue(
                converter.convert(ConversionContext(this, key), clazz)
                    ?: throw ValueNotFoundException("There is no value for '$key' key or it's null.")
            )
        }.value as T
    }

    /**
     * Function for reading and parsing the String values to actual objects or null.
     */
    @Suppress("UNCHECKED_CAST")
    override fun <T> parseOrNull(key: String, clazz: Class<out T>): T? {
        require(key.isNotBlank()) {
            "The key cannot be blank string."
        }
        return cache.computeIfAbsent(Pair(key, clazz)) {
            CachedValue(
                converter.convert(ConversionContext(this, key), clazz)
            )
        }.value as T?
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
    override fun <T> parseList(itemKeyPrefix: String, clazz: Class<out T>): List<T> {
        require(itemKeyPrefix.isNotBlank()) {
            "The itemKeyPrefix cannot be blank string."
        }
        // normalise prefix, add "." at the end to make processing easier and make usage foolproof
        val normalisedPrefix = normaliseListSearchKeyPrefix(itemKeyPrefix)
        return cache.computeIfAbsent(Pair(normalisedPrefix, clazz)) {
            CachedValue(
                parseCollectionTo(mutableListOf(), normalisedPrefix, itemKeyPrefix, clazz)
            )
        }.value as List<T>
    }

    /**
     * Function for reading and parsing a set of String values to a set of actual objects.
     */
    @Suppress("UNCHECKED_CAST")
    override fun <T> parseSet(itemKeyPrefix: String, clazz: Class<out T>): Set<T> {
        require(itemKeyPrefix.isNotBlank()) {
            "The itemKeyPrefix cannot be blank string."
        }
        // normalise prefix, add "." at the end to make processing easier and make usage foolproof
        val normalisedPrefix = normaliseListSearchKeyPrefix(itemKeyPrefix)
        return cache.computeIfAbsent(Pair(normalisedPrefix, clazz)) {
            CachedValue(
                parseCollectionTo(mutableSetOf(), normalisedPrefix, itemKeyPrefix, clazz)
            )
        }.value as HashSet<T>
    }

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is LayeredPropertyMapImpl) return false
        if (this === other) return true
        return properties == other.properties
    }

    override fun hashCode(): Int = properties.hashCode()

    private fun <T> parseCollectionTo(
        destination: MutableCollection<T>,
        normalisedPrefix: String,
        itemKeyPrefix: String,
        clazz: Class<out T>,
    ): Collection<T> {
        // Check the matching elements in entries
        val matchingEntries = entries.filter { it.key.startsWith(normalisedPrefix) }

        // If no matching elements in entries, return empty collection
        if (matchingEntries.isEmpty()) return destination

        // Checking that the structure has numbers in it, if not then throwing an exception
        val entryList = matchingEntries.takeIf {
            it.all { pair -> pair.key.removePrefix(normalisedPrefix).first().isDigit() }
        } ?: throw IllegalArgumentException("Prefix is invalid, only number is accepted after prefix.")

        // Group by the index
        // then convert it to a map one by one and pass it to the converter
        // 1 -> [corda.endpoints.1.url=localhost, corda.endpoints.1.protocolVersion=1]
        // 2 -> [corda.endpoints.2.url=localhost, corda.endpoints.2.protocolVersion=1]
        // 1 -> [corda.ledger.keys.1=ABC]
        val result = entryList.groupBy {
            getIndexedPrefix(it.key, normalisedPrefix)
        }.toSortedMap(indexedPrefixComparator).map { groupedEntry ->
            groupedEntry.key to (groupedEntry.value.map { it.key to it.value }).toLinkedHashMap()
        }.mapTo(destination) {
            // instead of the whole context, we are just passing a pre-processed properties to the converter one by one
            val map = it.second.map { item ->
                item.key.removePrefix(it.first.second) to item.value
            }.toLinkedHashMap()
            val itemContext = ConversionContext(LayeredPropertyMapImpl(map, converter), "")
            converter.convert(itemContext, clazz)
                ?: throw ValueNotFoundException("Error while converting $itemKeyPrefix prefix.")
        }
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
        return if (dotPos < 0) {
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
