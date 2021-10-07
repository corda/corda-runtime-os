@file:JvmName("WireUtils")

package net.corda.crypto.client.rpc

import net.corda.data.WireKeyValuePair

fun Map<String, String>.toWire() : List<WireKeyValuePair> {
    return map {
        WireKeyValuePair(it.key, it.value)
    }
}
