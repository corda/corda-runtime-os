package net.corda.layeredpropertymap

import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.v5.base.types.LayeredPropertyMap

/**
 * Extension function for converting the content of [LayeredPropertyMap] to a [KeyValuePairList].
 */
fun LayeredPropertyMap.toAvro(): KeyValuePairList = KeyValuePairList(
    entries.map {
        KeyValuePair(it.key, it.value)
    }
)