package net.corda.flow.utils

import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList

fun mutableKeyValuePairList() = KeyValuePairList(mutableListOf())
fun emptyKeyValuePairList() = KeyValuePairList(emptyList())

/**
 * Creates a [KeyValueStore] from a variable number of pairs of strings.
 * @param pairs Pairs of strings, the first is considered the key, the second the value.
 * @return A [KeyValueStore] containing the keys and values from the pairs.
 */
fun keyValueStoreOf(vararg pairs: Pair<String, String>) = KeyValueStore().apply {
    pairs.forEach {
        put(it.first, it.second)
    }
}

/**
 * Creates an avro generated [KeyValuePairList] from a Kotlin Map
 * @param map The Kotlin map
 * @return An avro [KeyValuePairList]
 */
fun keyValuePairListOf(map: Map<String, String>) = mutableKeyValuePairList().apply {
    map.entries.forEach {
        items.add(KeyValuePair(it.key, it.value))
    }
}

/**
 * A KeyValueStore which operates much like a map from the user perspective. Internally it is backed by an Avro array
 * which means serialization and deserialization are guaranteed to produce the same object, which is not the case with
 * Avro's own built in map type.
 * This functionality cannot be applied directly to the Avro array via extension functions, because Avro reserves some
 * of the standard map named methods for its own purposes.
 *
 * @param backingList The Avro array which backs this [KeyValueStore]. The [KeyValueStore] class itself is stateless,
 * all changes to the store are reflected directly in the array.
 */
class KeyValueStore(private val backingList: KeyValuePairList = mutableKeyValuePairList()) {

    private fun KeyValuePairList.setValue(key: String, value: String) {
        items.find { it.key == key }?.let {
            it.value = value
        } ?: run {
            items.add(KeyValuePair(key, value))
        }
    }

    operator fun set(key: String, value: String) = backingList.setValue(key, value)
    fun put(key: String, value: String) = set(key, value)
    operator fun get(key: String) = backingList.items.find { it.key == key }?.value

    /**
     * Importantly, this property exposes the mutable Avro array directly, no conversion is carried out.
     */
    val avro
        get() = backingList
}
