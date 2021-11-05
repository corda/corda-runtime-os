package net.corda.v5.membership.conversion

/**
 * The context from which we want to do the conversion and parsing from.
 *
 * @property store The [LayeredPropertyMap] containing all keys and their values.
 * @property storeClass The type of the store. Can be either [MemberContext] or [MGMContext].
 * @property key The key we are looking for in the store.
 */
open class ConversionContext(
    val store: LayeredPropertyMap,
    val storeClass: Class<out LayeredPropertyMap>,
    val key: String
) {
    val value: String? get() = store[key] ?: findValueByPattern(key)

    /**
     * Finds the value in the store based on the key and given pattern.
     */
    fun findValueByPattern(pattern: String): String? {
        return store.entries.firstOrNull {
            it.key.startsWith(key) && it.key.contains(pattern)
        }?.value
    }
}