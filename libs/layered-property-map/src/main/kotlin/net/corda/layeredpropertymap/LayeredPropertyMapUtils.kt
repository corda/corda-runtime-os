package net.corda.layeredpropertymap

import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.v5.base.types.LayeredPropertyMap
import java.nio.ByteBuffer

/**
 * Extension function for converting the content of [KeyValueStore] to a list of [KeyValuePair].
 * This conversion is required, because of the avro serialization done on the P2P layer.
 */
fun LayeredPropertyMap.toWire(): ByteBuffer =
    KeyValuePairList(entries.map { KeyValuePair(it.key, it.value) }).toByteBuffer()