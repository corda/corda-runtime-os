package net.corda.p2p.gateway.messaging.session

import com.typesafe.config.ConfigFactory
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.p2p.SessionPartitions
import net.corda.p2p.schema.Schema.Companion.SESSION_OUT_PARTITIONS
import net.corda.v5.base.util.contextLogger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class SessionPartitionMapperImpl(subscriptionFactory: SubscriptionFactory): SessionPartitionMapper {

    companion object {
        private val logger = contextLogger()
        const val CONSUMER_GROUP_ID = "session_partitions_mapper"
    }

    private val sessionPartitionsMapping = ConcurrentHashMap<String, List<Int>>()

    private var sessionPartitionSubscription = subscriptionFactory.createCompactedSubscription(
        SubscriptionConfig(CONSUMER_GROUP_ID, SESSION_OUT_PARTITIONS),
        SessionPartitionProcessor(sessionPartitionsMapping),
        ConfigFactory.empty()
    )

    private val startStopLock = ReentrantLock()
    private var running: Boolean = false

    override val isRunning: Boolean
        get() = running

    override fun start() {
        startStopLock.withLock {
            if (!running) {
                sessionPartitionSubscription.start()
                running = true
                logger.debug("Session partition mapper started.")
            }
        }
    }

    override fun stop() {
        startStopLock.withLock {
            if (running) {
                sessionPartitionSubscription.stop()
                sessionPartitionsMapping.clear()
                running = false
                logger.debug("Session partition mapper stopped.")
            }
        }
    }

    override fun getPartitions(sessionId: String): List<Int>? {
        return sessionPartitionsMapping[sessionId]
    }

    private class SessionPartitionProcessor(private val sessionPartitionMapping: ConcurrentHashMap<String, List<Int>>):
        CompactedProcessor<String, SessionPartitions> {
        override val keyClass: Class<String>
            get() = String::class.java
        override val valueClass: Class<SessionPartitions>
            get() = SessionPartitions::class.java

        override fun onSnapshot(currentData: Map<String, SessionPartitions>) {
            sessionPartitionMapping.putAll(currentData.map { it.key to it.value.partitions })
        }

        override fun onNext(
            newRecord: Record<String, SessionPartitions>,
            oldValue: SessionPartitions?,
            currentData: Map<String, SessionPartitions>
        ) {
            if (newRecord.value == null) {
                sessionPartitionMapping.remove(newRecord.key)
            } else {
                sessionPartitionMapping[newRecord.key] = newRecord.value!!.partitions
            }
        }

    }
}