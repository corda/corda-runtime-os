package net.corda.messaging.subscription.consumer

import java.util.concurrent.CompletableFuture

interface StateCache<K : Any, S : Any> {

    data class LastEvent(val topic: String, val partition: Int, val offset: Long, val safeMinOffset: Long)

    fun subscribe(cacheReadyCallback: () -> Unit)

    fun get(key: K): S?

    fun write(key: K, value: S?, sourceEventOffset: LastEvent): CompletableFuture<Unit>

    fun getMaxOffsetsByPartition(partitions: List<Int>): Map<Int, Long>

    fun getMinOffsetsByPartition(partitions: List<Int>): Map<Int, Long>

    fun isOffsetGreaterThanMaxSeen(partition: Int, offset: Long): Boolean
}