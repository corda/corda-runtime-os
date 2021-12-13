@file:JvmName("WireUtils")

package net.corda.crypto.client.rpc

import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList

fun Map<String, String>.toWire() : KeyValuePairList {
    return KeyValuePairList(
        map {
            KeyValuePair(it.key, it.value)
        }
    )
}
