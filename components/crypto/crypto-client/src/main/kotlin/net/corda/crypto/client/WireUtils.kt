@file:JvmName("WireUtils")

package net.corda.crypto.client

import net.corda.crypto.CryptoConsts
import net.corda.crypto.CryptoPublishResult
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoRequestContext
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture

val emptyKeyValuePairList = KeyValuePairList(emptyList())

fun CryptoRequestContext.toCryptoPublishResult() = CryptoPublishResult(requestId)

fun List<CompletableFuture<Unit>>.waitAll() = forEach { it.get() }

inline fun <reified CALLER> createWireRequestContext(
    tenantId: String,
    hsmLabel: String?
): CryptoRequestContext {
    val other = if (hsmLabel.isNullOrBlank()) {
        emptyKeyValuePairList
    } else {
        KeyValuePairList(
            listOf(
                KeyValuePair(CryptoConsts.Request.HSM_LABEL_CONTEXT_KEY, hsmLabel)
            )
        )
    }
    return CryptoRequestContext(
        CALLER::class.simpleName,
        Instant.now(),
        UUID.randomUUID().toString(),
        tenantId,
        other
    )
}

fun Map<String, String>.toWire(): KeyValuePairList {
    return KeyValuePairList(
        map {
            KeyValuePair(it.key, it.value)
        }
    )
}