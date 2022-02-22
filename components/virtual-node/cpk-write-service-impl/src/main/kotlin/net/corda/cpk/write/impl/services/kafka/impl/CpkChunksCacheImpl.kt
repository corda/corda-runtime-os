package net.corda.cpk.write.impl.services.kafka.impl

import net.corda.cpk.write.impl.services.kafka.CpkChunksCache
import net.corda.cpk.write.impl.CpkChunkId
import net.corda.cpk.write.impl.services.kafka.AvroTypesTodo
import net.corda.cpk.write.impl.services.kafka.toCorda
import net.corda.data.chunking.Chunk
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.v5.base.annotations.VisibleForTesting
import net.corda.v5.base.util.contextLogger
import java.util.concurrent.ConcurrentHashMap

// Needs to be made a CPK checksum cache. What will happen if say some chunks of a CPK are on Kafka but not all?
// Should we identify somehow that all chunks of a CPK are on kafka and only then that CPK checksum could be cached?
// In theory, if we say publishing to Kafka will be transactional, i.e. either all chunks or none, that should mean
// all chunks for a CPK should be in Kafka.
// Or maybe check for a null byte [CpkChunk] meaning all of the chunks were uploaded to Kafka. Should be quite safe?
/**
 * CPK chunks cache. Caches only what identifies uniquely a CPK chunk which is its checksum + chunk id.
 * The cache will get updated every time a new CPK chunk entry gets written to Kafka.
 */
class CpkChunksCacheImpl(
    subscriptionFactory: SubscriptionFactory,
    subscriptionConfig: SubscriptionConfig,
    nodeConfig: SmartConfig = SmartConfigImpl.empty()
) : CpkChunksCache {
    companion object {
        val logger = contextLogger()
    }

    @VisibleForTesting
    internal val cpkChunkIds: MutableMap<CpkChunkId, CpkChunkId> = ConcurrentHashMap()

    // TODO Add config to the below
    @VisibleForTesting
    internal val compactedSubscription =
        subscriptionFactory.createCompactedSubscription(subscriptionConfig, CacheSynchronizer(), nodeConfig)

    override val isRunning: Boolean
        get() = compactedSubscription.isRunning

    override fun start() {
        compactedSubscription.start()
    }

    override fun stop() {
        compactedSubscription.close()
    }

    override fun contains(checksum: CpkChunkId) = checksum in cpkChunkIds.keys

    override fun add(cpkChunkId: CpkChunkId) {
        cpkChunkIds[cpkChunkId] = cpkChunkId
    }

    inner class CacheSynchronizer : CompactedProcessor<AvroTypesTodo.CpkChunkIdAvro, Chunk> {
        override val keyClass: Class<AvroTypesTodo.CpkChunkIdAvro>
            get() = AvroTypesTodo.CpkChunkIdAvro::class.java
        override val valueClass: Class<Chunk>
            get() = Chunk::class.java

        // TODO: Not good that we need to load AvroTypesTodo.CpkChunk back in memory for now reason.
        override fun onSnapshot(currentData: Map<AvroTypesTodo.CpkChunkIdAvro, Chunk>) {
            currentData.forEach { (cpkChunkIdAvro, _) ->
                // treat cpkInfoChecksums as a concurrent set, hence not care about its values.
                val cpkChunkId = cpkChunkIdAvro.toCorda()
                add(cpkChunkId)
                logger.debug(
                    "Added CPK chunk to cache of id cpkChecksum: ${cpkChunkId.cpkChecksum} partNumber: ${cpkChunkId.partNumber}"
                )
            }
        }

        override fun onNext(
            newRecord: Record<AvroTypesTodo.CpkChunkIdAvro, Chunk>,
            oldValue: Chunk?,
            currentData: Map<AvroTypesTodo.CpkChunkIdAvro, Chunk>
        ) {
            // TODO add checks with oldValue: CpkInfo? that matches memory state
            //  also assert that newRecord.topic is the same with ours just in case?
            val cpkChunkIdAvro = newRecord.key
            val cpkChunkId = cpkChunkIdAvro.toCorda()
            add(cpkChunkId)
            logger.debug(
                "Added CPK chunk to cache of id cpkChecksum: ${cpkChunkId.cpkChecksum} partNumber: ${cpkChunkId.partNumber}"
            )
        }
    }
}