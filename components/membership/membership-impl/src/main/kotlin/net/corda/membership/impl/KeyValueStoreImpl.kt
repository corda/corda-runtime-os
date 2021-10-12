package net.corda.membership.impl

import net.corda.membership.impl.serialization.EndpointInfoStringConverter
import net.corda.membership.impl.serialization.PartyStringConverter
import net.corda.v5.application.identity.Party
import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.v5.membership.identity.*
import java.lang.ClassCastException
import java.security.PublicKey
import java.time.Instant
import java.util.SortedMap
import java.util.concurrent.ConcurrentHashMap

open class KeyValueStoreImpl(
    private val properties: SortedMap<String, String>,
    val encodingService: KeyEncodingService
): KeyValueStore {

    companion object {
        private const val DEFAULT_PRIMITIVE_KEY = "DEFAULT"
        private val BUILTIN_CONVERTERS = mapOf(
            EndpointInfo::class.java to EndpointInfoStringConverter::class.java,
            Party::class.java to PartyStringConverter::class.java
        )
    }

    private val cache = ConcurrentHashMap<String, Any?>()

    override operator fun get(key: String): String? = properties[key]

    @Transient
    override val keys: Set<String> = properties.keys
    @Transient
    override val entries: Set<Map.Entry<String, String>> = properties.entries

    /**
     * Function for reading and parsing the String values to actual objects.
     */
    @Suppress("UNCHECKED_CAST")
    override fun <T> parse(key: String, clazz: Class<out T>, stringObjectConverter: StringObjectConverter<T>?): T {

        // 1. Check if value already is in our cache, if yes, return that value
        val cachedValue = cache[key]
        cachedValue?.let {
            println("cache")
            return cachedValue as? T  ?: throw ClassCastException("Casting failed for $cachedValue at $key.")
        }

        val converter = findConverter(stringObjectConverter, clazz)

        // 2. Check if value exists in entries, if not, throw exception since impossible to convert
        val values: List<Pair<String, String>> = properties.filter {
            it.key.startsWith(key)
        }.map { it.key to it.value }

        if (values.isEmpty()) throw ValueNotFoundException("There is no value for '$key' key.")

        // 3. Convert the string value using the provided or built-in converter or our built-in primitive converter if not provided
        val convertedValue = converter?.convert(
            KeyValueStoreImpl(values.toMap().toSortedMap(), encodingService),
            clazz
        ) ?: convert(
            values.singleOrNull()?.second
                ?: throw IllegalStateException("Complex type cannot be converted with default converter, please use explicit converter class"),
            clazz
        )

        // 4. Assign the converted value in the cache and return it
        cache[key] = convertedValue
        return convertedValue as T
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
        clazz: Class<out T>,
        converter: StringObjectConverter<T>?
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
        // 1 = ABC
        val strippedEntries = matchingEntries.map {
            it.key.removePrefix(normalisedPrefix) to it.value
        }.takeIf {
            it.all { it.first[0].isDigit() }
        } ?: throw IllegalArgumentException("Prefix is invalid, only number is accepted after prefix")

        // Find converter, either provided or built-in
        val stringObjectConverter = findConverter(converter, clazz)

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
            if (stringObjectConverter != null) {
                stringObjectConverter.convert(KeyValueStoreImpl(it.second.toSortedMap(), encodingService), clazz)
            } else {
                // If no provided or built-in converter found, use the primitive converter
                if (it.second.size > 1) {
                    throw IllegalStateException("Default converter cannot be used on complex structures, " +
                            "please provide a StringObjectConverter")
                }
                convert(it.second.values.first(), clazz) as T
            }
        }
        cache[simplePrefix] = result
        return result
    }

    /**
     * Function for reading and parsing the String values to actual objects or null.
     */
    @Suppress("UNCHECKED_CAST")
    override fun <T> parseOrNull(key: String, clazz: Class<out T>, stringObjectConverter: StringObjectConverter<T>?): T? {

        // 1. Check if value already in cache, if yes, return that value
        val cachedValue = cache[key]
        cachedValue?.let {
            return cachedValue as T?
        }

        // 2. Check if value present in entries, if not or the value is null then return null
        val value = get(key) ?: return null

        // 3. Find the converter, either provided or builtin
        val converter = findConverter(stringObjectConverter, clazz)

        // 4. Convert the value with the converter (provided or builtin), if no converter is found, use our default primitive converter
        val convertedValue = converter?.convert(
            KeyValueStoreImpl(sortedMapOf(DEFAULT_PRIMITIVE_KEY to value), encodingService),
            clazz
        ) ?: convert(get(key), clazz)

        // 5. Assign converted value and return it
        cache[key] = convertedValue
        return convertedValue
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> convert(value: String?, clazz: Class<out T>): T? {
        if (value == null) {
            return null
        }
        return when (clazz.kotlin) {
            Int::class -> value.toInt() as T
            Long::class -> value.toLong() as T
            Short::class -> value.toShort() as T
            Float::class -> value.toFloat() as T
            Double::class -> value.toDouble() as T
            String::class -> value as T
            MemberX500Name::class -> MemberX500Name.parse(value) as T
            PublicKey::class -> encodingService.decodePublicKey(value) as T
            Instant::class -> Instant.parse(value) as T
            else -> throw IllegalStateException("Parsing failed due to unknown ${clazz.name} type.")
        }
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

    /**
     * Tries to find the most suitable converter from the built-in ones.
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T> findConverter(stringObjectConverter: StringObjectConverter<T>?,
                                  clazz: Class<out T>): StringObjectConverter<T>? {

        val converterClass = BUILTIN_CONVERTERS.entries.firstOrNull {
            it.key == clazz
        }?.value

        val builtinConverter = converterClass?.constructors?.firstOrNull()?.newInstance() as? StringObjectConverter<T>

        return stringObjectConverter ?: builtinConverter
    }
}

inline fun <reified T> KeyValueStore.parse(key: String, converter: StringObjectConverter<T>? = null): T {
    return parse(key, T::class.java, converter)
}

inline fun <reified T> KeyValueStore.parseOrNull(key: String, converter: StringObjectConverter<T>? = null): T? {
    return parseOrNull(key, T::class.java, converter)
}

inline fun <reified T> KeyValueStore.parseList(itemKeyPrefix: String, converter: StringObjectConverter<T>): List<T> {
    return parseList(itemKeyPrefix, T::class.java, converter)
}