package net.corda.layeredpropertymap

import net.corda.v5.base.types.LayeredPropertyMap

/**
 * The context from which we want to do the conversion and parsing from.
 *
 * @property store The [LayeredPropertyMap] containing all keys and their values.
 * @property storeClass The type of the store.
 * @property key The key we are looking for in the store.
 */
open class ConversionContext(
    val store: LayeredPropertyMap,
    val storeClass: Class<out LayeredPropertyMap>,
    val key: String
) {
    fun value(): String? = store[key]

    fun value(subKey: String): String? = store[fullKey(subKey)]

    private fun fullKey(subKey: String): String =
        when {
            key.isEmpty() -> subKey
            key.endsWith(".") -> "$key$subKey"
            else -> "$key.$subKey"
        }
}