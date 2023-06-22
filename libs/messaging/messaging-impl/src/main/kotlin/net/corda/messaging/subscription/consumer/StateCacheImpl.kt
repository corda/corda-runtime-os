package net.corda.messaging.subscription.consumer

import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messagebus.api.producer.CordaProducer
import net.corda.messagebus.api.producer.CordaProducerRecord
import net.corda.messaging.config.ResolvedSubscriptionConfig
import java.util.concurrent.CompletableFuture

class StateCacheImpl<K : Any, S : Any>(
    private val config: ResolvedSubscriptionConfig,
    private val consumer: CordaConsumer<K, S>,
    private val producer: CordaProducer
) : StateCache<K, S> {

    private data class CachedRecord<K : Any, S : Any>(val lastEvent: StateCache.LastEvent, val key: K, val state: S)

    private var isRunning = false
    private val cachedData = mutableMapOf<K, CachedRecord<K, S>>()
    private val partitionMaxOffsets = mutableMapOf<Int, Long>()
    private val partitionSafeMinOffset = mutableMapOf<Int, Long>()

    override fun subscribe(cacheReadyCallback: () -> Unit) {
        if (!isRunning) {
            consumer.subscribe(config.topic)
            val partitions = consumer.getPartitions(config.topic)
            consumer.assign(partitions)
            val endOffsets = consumer.endOffsets(partitions)
            val snapshotEnds = endOffsets.toMutableMap()
            consumer.seekToBeginning(partitions)

            while (snapshotEnds.isNotEmpty()) {
                val consumerRecords = consumer.poll(config.pollTimeout)

                consumerRecords.forEach { record ->
                    val key = record.key
                    record.value?.let {
                        cachedData[key] = CachedRecord(readHeader(record.headers[0].second), key, it)
                    } ?: cachedData.remove(key)
                }

                for (offsets in endOffsets) {
                    val partition = offsets.key
                    if (consumer.position(partition) >= offsets.value) {
                        snapshotEnds.remove(partition)
                    }
                }
            }
        }

        isRunning = true

        consumer.close()

        cacheReadyCallback()
    }

    override fun getMaxOffsetsByPartition(partitions: List<Int>): Map<Int, Long> {
        return partitionMaxOffsets
    }

    override fun getMinOffsetsByPartition(partitions: List<Int>): Map<Int, Long> {
        return partitions.associateWith { partition ->
            partitionSafeMinOffset.getOrDefault(partition, 0)
        }
    }

    override fun isOffsetGreaterThanMaxSeen(partition: Int, offset: Long): Boolean {
        return offset > partitionMaxOffsets.getOrDefault(partition, 0L)
    }

    override fun write(key: K, value: S?, lastEvent: StateCache.LastEvent): CompletableFuture<Unit> {
        val record = CordaProducerRecord(
            config.topic,
            key,
            value,
            headers = listOf(Pair("state.last.event", writeHeader(lastEvent)))
        )

        val sendFuture = CompletableFuture<Unit>()
        producer.send(record) { e ->
            if (e == null) {
                value?.let {
                    addValue(key, CachedRecord(lastEvent, key, it))
                } ?: removeValue(key)

                sendFuture.complete(Unit)
            } else sendFuture.completeExceptionally(e)
        }

        return sendFuture
    }

    override fun get(key: K): S? {
        return cachedData[key]?.let { it.state }
    }

    private fun addValue(key: K, entry: CachedRecord<K, S>) {
        cachedData[key] = entry

        val partition = entry.lastEvent.partition
        val existingMax = partitionMaxOffsets.getOrDefault(partition, 0)
        if (existingMax < entry.lastEvent.offset) {
            partitionMaxOffsets[partition] = entry.lastEvent.offset
        }

        val existingSafeMin = partitionSafeMinOffset.getOrDefault(partition, 0)
        if (existingSafeMin < entry.lastEvent.safeMinOffset) {
            partitionSafeMinOffset[partition] = entry.lastEvent.safeMinOffset + 1
        }
    }

    private fun removeValue(key: K) {
        cachedData.remove(key)
    }

    private fun writeHeader(lastEvent: StateCache.LastEvent): String {
        return "${lastEvent.topic}:${lastEvent.partition}:${lastEvent.offset}:${lastEvent.safeMinOffset}"
    }

    private fun readHeader(lastEventHeader: String): StateCache.LastEvent {
        val parts = lastEventHeader.split(":")
        return StateCache.LastEvent(parts[0], parts[1].toInt(), parts[2].toLong(), parts[3].toLong())
    }
}