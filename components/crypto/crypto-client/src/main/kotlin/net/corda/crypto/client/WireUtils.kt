@file:JvmName("WireUtils")

package net.corda.crypto.client

import net.corda.crypto.clients.CryptoPublishResult
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoRequestContext
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture

val emptyKeyValuePairList = KeyValuePairList(emptyList())

fun CryptoRequestContext.toCryptoPublishResult() = CryptoPublishResult(requestId)

fun List<CompletableFuture<Unit>>.waitAll() = forEach { it.get() }

fun createWireRequestContext(tenantId: String): CryptoRequestContext = CryptoRequestContext(
    HSMRegistrarPublisher::class.simpleName,
    Instant.now(),
    UUID.randomUUID().toString(),
    tenantId,
    emptyKeyValuePairList
)

fun createWireRequestContext(tenantId: String, context: KeyValuePairList): CryptoRequestContext = CryptoRequestContext(
    HSMRegistrarPublisher::class.simpleName,
    Instant.now(),
    UUID.randomUUID().toString(),
    tenantId,
    context
)

fun Map<String, String>.toWire(): KeyValuePairList {
    return KeyValuePairList(
        map {
            KeyValuePair(it.key, it.value)
        }
    )
}