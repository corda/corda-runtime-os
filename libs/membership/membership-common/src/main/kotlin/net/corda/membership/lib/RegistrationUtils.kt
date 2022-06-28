package net.corda.membership.lib

import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList

/**
 * Transforms [KeyValuePairList] into map.
 */
fun KeyValuePairList.toMap() = items.associate { it.key to it.value }

/**
 * Transforms map into [KeyValuePairList].
 */
fun Map<String, String>.toWire(): KeyValuePairList {
    return KeyValuePairList(
        map {
            KeyValuePair(it.key, it.value)
        }
    )
}
