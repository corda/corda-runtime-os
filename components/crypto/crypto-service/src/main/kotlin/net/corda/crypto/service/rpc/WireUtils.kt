@file:JvmName("WireUtils")

package net.corda.crypto.service.rpc

import net.corda.data.KeyValuePair

fun List<KeyValuePair>.toMap() : Map<String, String> {
    val map = mutableMapOf<String, String>()
    forEach {
        map[it.key] = it.value
    }
    return map
}
