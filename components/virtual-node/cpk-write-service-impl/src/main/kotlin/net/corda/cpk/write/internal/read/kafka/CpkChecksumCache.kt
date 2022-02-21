package net.corda.cpk.write.internal.read.kafka

import net.corda.cpk.write.CpkInfo
import net.corda.lifecycle.*
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.v5.base.annotations.VisibleForTesting
import net.corda.v5.base.util.contextLogger
import net.corda.v5.crypto.SecureHash
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.util.concurrent.ConcurrentHashMap

/**
 * CPK checksums cache. The cache will get updated every time a new entry gets written to Kafka.
 */
@Component
class CpkChecksumCache(
    subscriptionConfig: SubscriptionConfig,
    @Reference
    private val subscriptionFactory: SubscriptionFactory,
) : Lifecycle {
    companion object {
        val logger = contextLogger()
    }

    @VisibleForTesting
    internal val cpkChecksums: MutableMap<SecureHash, SecureHash> = ConcurrentHashMap()

    // TODO Need to add config to the below
    @VisibleForTesting
    internal val compactedSubscription =
        subscriptionFactory.createCompactedSubscription(subscriptionConfig, CacheSynchronizer())

    inner class CacheSynchronizer : CompactedProcessor<SecureHash, CpkInfo> {
        override val keyClass: Class<SecureHash>
            get() = SecureHash::class.java
        override val valueClass: Class<CpkInfo>
            get() = CpkInfo::class.java

        override fun onSnapshot(currentData: Map<SecureHash, CpkInfo>) {
            currentData.forEach { (checksum, _) ->
                // treat cpkInfoChecksums as a concurrent set, hence not care about its values.
                cpkChecksums[checksum] = checksum
                logger.debug("Added CPK checksum to cache: $checksum")
            }
        }

        override fun onNext(
            newRecord: Record<SecureHash, CpkInfo>,
            oldValue: CpkInfo?,
            currentData: Map<SecureHash, CpkInfo>
        ) {
            // TODO add checks with oldValue: CpkInfo? that matches memory state
            //  also assert that newRecord.topic is the same with ours just in case?
            val checksum = newRecord.key
            cpkChecksums.putIfAbsent(checksum, checksum)
            logger.debug("Added CPK checksum to cache: $checksum")
        }
    }

    fun contains(checksum: SecureHash) = checksum in cpkChecksums.keys

    override val isRunning: Boolean
        get() = compactedSubscription.isRunning

    override fun start() {
        compactedSubscription.start()
    }

    override fun stop() {
        compactedSubscription.close()
    }
}