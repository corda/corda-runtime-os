package net.corda.cpi.write.impl.kafka

import com.typesafe.config.Config
import net.corda.cpi.read.CPIRead
import net.corda.cpi.read.CPISegmentReader
import net.corda.cpi.read.factory.CPIReadFactory
import net.corda.cpi.utils.CPI_LIST_TOPIC_NAME
import net.corda.cpi.utils.CPI_PUBLISHER_CLIENT_ID
import net.corda.cpi.utils.RPC_CPI_CLIENT_NAME
import net.corda.cpi.utils.RPC_CPI_GROUP_NAME
import net.corda.cpi.utils.RPC_CPI_TOPIC_NAME
import net.corda.cpi.utils.toSerializedString
import net.corda.cpi.write.CPIWrite
import net.corda.data.packaging.CPISegmentRequest
import net.corda.data.packaging.CPISegmentResponse
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.RPCConfig
import net.corda.packaging.CPI
import net.corda.packaging.converters.toAvro
import net.corda.v5.base.util.contextLogger
import org.slf4j.Logger
import java.lang.IllegalStateException
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock


//TODO: Combine the readSegment and readfactory?
class CPIWriteImplKafka(private val subscriptionFactory: SubscriptionFactory,
                        private val publisherFactory: PublisherFactory,
                        private val nodeConfig: Config,
                        private val cpiReadFactory: CPIReadFactory): CPIWrite {

    private val rpcConfig = RPCConfig(RPC_CPI_GROUP_NAME, RPC_CPI_CLIENT_NAME, RPC_CPI_TOPIC_NAME, CPISegmentRequest::class.java, CPISegmentResponse::class.java)

    private val lock = ReentrantLock()
    @Volatile
    private var stopped = true
    private lateinit var cpiListCallbackHandle: AutoCloseable
    private lateinit var cpiRead: CPIRead
    private lateinit var cpiSegmentReader: CPISegmentReader
    private lateinit var cpiSegmentProcessor: CPISegmentProcessor
    private lateinit var publisher: Publisher
    private val publisherConfig = PublisherConfig(CPI_PUBLISHER_CLIENT_ID)

    companion object {
        val logger: Logger = contextLogger()
    }

    override fun start() {
        lock.withLock {
            if (stopped) {
                stopped = false
                cpiRead = cpiReadFactory.createCPIRead(nodeConfig)
                cpiSegmentReader = cpiReadFactory.createCPIReadSegment(nodeConfig)
                cpiSegmentProcessor = CPISegmentProcessor(rpcConfig, nodeConfig, cpiSegmentReader, subscriptionFactory)
                publisher = publisherFactory.createPublisher(publisherConfig, nodeConfig)
                cpiRead.start()
                cpiSegmentReader.start()
                cpiListCallbackHandle = cpiRead.registerCallback { changedKeys, currentSnapshot ->
                    this.onUpdateCPIIdentifier(changedKeys, currentSnapshot)
                }
                cpiSegmentProcessor.start()
            }
        }
    }

    override fun stop() {
        lock.withLock {
            if (!stopped) {
                stopped = true
                cpiListCallbackHandle.close()
                cpiRead.stop()
                cpiSegmentReader.stop()
                cpiSegmentProcessor.stop()
            }
        }
    }

    override val isRunning: Boolean
        get() = !stopped

    private fun onUpdateCPIIdentifier(changedKeys: Set<CPI.Identifier>, currentSnapshot: Map<CPI.Identifier, CPI.Metadata>) {
        val records = changedKeys.map { key ->
            val cpiMetadata = currentSnapshot[key]
            if (cpiMetadata == null) {
                logger.error("Null metadata found in current snapshot")
                throw IllegalStateException("Null metadata found in current snapshot")
            }
            val avroCPIMetadata = cpiMetadata.toAvro()
            val identifierString = key.toSerializedString()
            Record(CPI_LIST_TOPIC_NAME, identifierString, avroCPIMetadata )}
        val res = publisher.publish(records)
        res.forEach { it.get() }
    }
}
