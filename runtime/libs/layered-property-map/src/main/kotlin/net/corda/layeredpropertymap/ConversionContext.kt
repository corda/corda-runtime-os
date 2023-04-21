package net.corda.layeredpropertymap

import net.corda.v5.base.types.LayeredPropertyMap

/**
 * The context from which we want to do the conversion and parsing from.
 *
 * @property map The [LayeredPropertyMap] containing all keys and their values.
 * @property key The key we are looking for in the store. If the context references item in the list then it would be
 * empty string.
 */
open class ConversionContext(
    val map: LayeredPropertyMap,
    val key: String
) {
    /**
     * Returns true if the context describes an item in a list.
     */
    val isListItem: Boolean get() = key.isEmpty()

    /**
     * Returns a string value (or null if it's not found) for objects which occupy single map item -
     * like Boolean, Int, MemberX500Name (despite being complex object it's parsed from single string), PublicKey
     * (as well despite being complex object it's parsed from single string), etc.
     */
    fun value(): String? = map[key]

    /**
     * Return a string value(or null if it's not found) for objects occupying several map items, like EndpointInfo
     * which uses two items "<prefix>.url: and "<prefix>.protocolVersion"
     */
    fun value(subKey: String): String? = map[fullKey(subKey)]

    private fun fullKey(subKey: String): String =
        when {
            key.isEmpty() -> subKey
            key.endsWith(".") -> "$key$subKey"
            else -> "$key.$subKey"
        }
}