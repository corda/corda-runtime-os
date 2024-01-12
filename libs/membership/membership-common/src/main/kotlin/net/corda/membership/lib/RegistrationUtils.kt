package net.corda.membership.lib

import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.v5.base.types.LayeredPropertyMap
import net.corda.v5.membership.MemberContext

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

/**
 * Transforms LayeredPropertyMap into [KeyValuePairList].
 */
fun LayeredPropertyMap.toWire(): KeyValuePairList {
    return KeyValuePairList(
        entries.map {
            KeyValuePair(it.key, it.value)
        }
    )
}

/**
 * Transforms [MemberContext] into map.
 */
fun MemberContext.toMap() = entries.associate { it.key to it.value }
