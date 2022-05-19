@file:JvmName("WireUtils")

package net.corda.crypto.client.impl

import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoRequestContext
import java.time.Instant
import java.util.UUID

val emptyKeyValuePairList = KeyValuePairList(emptyList())

inline fun <reified CALLER> createWireRequestContext(
    tenantId: String
): CryptoRequestContext {
    return CryptoRequestContext(
        CALLER::class.simpleName,
        Instant.now(),
        UUID.randomUUID().toString(),
        tenantId,
        emptyKeyValuePairList
    )
}

fun Map<String, String>.toWire(): KeyValuePairList {
    return KeyValuePairList(
        map {
            KeyValuePair(it.key, it.value)
        }
    )
}

fun List<KeyValuePair>.toMap() : Map<String, String> {
    val map = mutableMapOf<String, String>()
    forEach {
        map[it.key] = it.value
    }
    return map
}