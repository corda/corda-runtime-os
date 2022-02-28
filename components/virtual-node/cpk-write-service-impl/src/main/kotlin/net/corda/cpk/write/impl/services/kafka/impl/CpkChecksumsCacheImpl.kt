package net.corda.cpk.write.impl.services.kafka.impl

import net.corda.chunking.toCorda
import net.corda.cpk.write.impl.services.kafka.CpkChecksumsCache
import net.corda.data.chunking.Chunk
import net.corda.data.chunking.CpkChunkId
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.v5.base.annotations.VisibleForTesting
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.v5.crypto.SecureHash
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap

/**
 * This cache will get updated everytime a zero chunk is pushed to Kafka and gets picked up by [CacheSynchronizer].
 */
class CpkChecksumsCacheImpl(
    subscriptionFactory: SubscriptionFactory,
    subscriptionConfig: SubscriptionConfig,
    nodeConfig: SmartConfig = SmartConfigImpl.empty()
) : CpkChecksumsCache {
    companion object {
        val logger = contextLogger()

        private fun ByteBuffer.isZeroChunk() = this.limit() == 0
    }

    private val cpkChecksums: MutableMap<SecureHash, SecureHash> = ConcurrentHashMap()

    @VisibleForTesting
    internal val compactedSubscription =
        subscriptionFactory.createCompactedSubscription(subscriptionConfig, CacheSynchronizer(), nodeConfig)

    override val isRunning: Boolean
        get() = compactedSubscription.isRunning

    override fun start() =
        compactedSubscription.start()

    override fun stop() =
        compactedSubscription.close()

    override fun getCachedCpkIds() =
        cpkChecksums.keys

    override fun add(cpkChecksum: SecureHash) {
        logger.debug { "Adding CPK checksum to cache $cpkChecksum" }
        cpkChecksums[cpkChecksum] = cpkChecksum
    }

    inner class CacheSynchronizer : CompactedProcessor<CpkChunkId, Chunk> {
        override val keyClass: Class<CpkChunkId>
            get() = CpkChunkId::class.java
        override val valueClass: Class<Chunk>
            get() = Chunk::class.java

        override fun onSnapshot(currentData: Map<CpkChunkId, Chunk>) {
            currentData.forEach { (cpkChunkId, cpkChunk) ->
                updateCacheOnZeroChunk(cpkChunkId, cpkChunk)
            }
        }

        @Suppress("ForbiddenComment")
        override fun onNext(
            newRecord: Record<CpkChunkId, Chunk>,
            oldValue: Chunk?,
            currentData: Map<CpkChunkId, Chunk>
        ) {
            // TODO: add checks with oldValue: CpkInfo? that matches memory state
            //  also assert that newRecord.topic is the same with ours just in case?
            val cpkChunkId = newRecord.key
            val cpkChunk = newRecord.value
            updateCacheOnZeroChunk(cpkChunkId, cpkChunk)
        }

        private fun updateCacheOnZeroChunk(cpkChunkId: CpkChunkId, cpkChunk: Chunk?) {
            if (cpkChunk?.data?.isZeroChunk() == true) {
                val cpkChecksum = cpkChunkId.cpkChecksum.toCorda()
                add(cpkChecksum)
            }
        }
    }
}