package net.corda.cpk.write.internal.read.kafka

import net.corda.cpk.write.internal.read.AvroTypesTodo
import net.corda.cpk.write.internal.read.toCorda
import net.corda.cpk.write.types.CpkChunkId
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.lifecycle.*
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.v5.base.annotations.VisibleForTesting
import net.corda.v5.base.util.contextLogger
import java.util.concurrent.ConcurrentHashMap

/**
 * CPK chunks cache. Caches only what identifies uniquely a CPK chunk which is its checksum + chunk id.
 * The cache will get updated every time a new CPK chunk entry gets written to Kafka.
 */
class CpkChunksCacheImpl(
    subscriptionFactory: SubscriptionFactory,
    subscriptionConfig: SubscriptionConfig,
    nodeConfig: SmartConfig = SmartConfigImpl.empty()
) : CpkChunksCache, Lifecycle {
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

    inner class CacheSynchronizer : CompactedProcessor<AvroTypesTodo.CpkChunkIdAvro, AvroTypesTodo.CpkChunkAvro> {
        override val keyClass: Class<AvroTypesTodo.CpkChunkIdAvro>
            get() = AvroTypesTodo.CpkChunkIdAvro::class.java
        override val valueClass: Class<AvroTypesTodo.CpkChunkAvro>
            get() = AvroTypesTodo.CpkChunkAvro::class.java

        // TODO: Not good that we need to load AvroTypesTodo.CpkChunk back in memory for now reason.
        override fun onSnapshot(currentData: Map<AvroTypesTodo.CpkChunkIdAvro, AvroTypesTodo.CpkChunkAvro>) {
            currentData.forEach { (cpkChunkIdAvro, _) ->
                // treat cpkInfoChecksums as a concurrent set, hence not care about its values.
                val cpkChunkId = cpkChunkIdAvro.toCorda()
                cpkChunkIds[cpkChunkId] = cpkChunkId
                logger.debug(
                    "Added CPK chunk to cache of id cpkChecksum: ${cpkChunkId.cpkChecksum} partNumber: ${cpkChunkId.partNumber}"
                )
            }
        }

        override fun onNext(
            newRecord: Record<AvroTypesTodo.CpkChunkIdAvro, AvroTypesTodo.CpkChunkAvro>,
            oldValue: AvroTypesTodo.CpkChunkAvro?,
            currentData: Map<AvroTypesTodo.CpkChunkIdAvro, AvroTypesTodo.CpkChunkAvro>
        ) {
            // TODO add checks with oldValue: CpkInfo? that matches memory state
            //  also assert that newRecord.topic is the same with ours just in case?
            val cpkChunkIdAvro = newRecord.key
            val cpkChunkId = cpkChunkIdAvro.toCorda()
            cpkChunkIds.putIfAbsent(cpkChunkId, cpkChunkId)
            logger.debug(
                "Added CPK chunk to cache of id cpkChecksum: ${cpkChunkId.cpkChecksum} partNumber: ${cpkChunkId.partNumber}"
            )
        }
    }
}