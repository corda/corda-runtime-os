package net.corda.cpi.read.impl.kafka

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import net.corda.cpi.read.CPIRead
import net.corda.cpi.read.CPIListener
import net.corda.cpi.utils.RPC_CPI_CLIENT_NAME
import net.corda.cpi.utils.RPC_CPI_GROUP_NAME
import net.corda.cpi.utils.RPC_CPI_TOPIC_NAME
import net.corda.data.packaging.CPISegmentRequest
import net.corda.data.packaging.CPISegmentResponse
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.RPCConfig
import net.corda.packaging.CPI
import org.osgi.service.component.annotations.Component
import java.io.InputStream
import java.lang.IllegalArgumentException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@Component(service = [CPIRead::class])
class CPIReadImplKafka(
    subscriptionFactory: SubscriptionFactory,
    publisherFactory: PublisherFactory,
    nodeConfig: Config = ConfigFactory.empty()
) : CPIRead {

    private val lock = ReentrantLock()

    private val rpcConfig = RPCConfig(RPC_CPI_GROUP_NAME,
                                      RPC_CPI_CLIENT_NAME,
                                      RPC_CPI_TOPIC_NAME, CPISegmentRequest::class.java, CPISegmentResponse::class.java)

    @Volatile
    private var stopped = true
    private val cpiListHandler: CPIListHandler = CPIListHandler(subscriptionFactory, nodeConfig)
    private  val streamReader = CPIStreamReaderImpl(rpcConfig, nodeConfig, publisherFactory)

    override fun registerCallback(cpiListener: CPIListener): AutoCloseable {
        return cpiListHandler.registerCPIListCallback(cpiListener)
    }

    override fun getCPI(cpiIdentifier: CPI.Identifier): CompletableFuture<InputStream> {
        val cpiMetadata = cpiListHandler.cpiMetadata[cpiIdentifier]
        val fileHash = cpiMetadata?.hash
            ?: return CompletableFuture.failedFuture(IllegalArgumentException("Unknown CPI identifier. Name = ${cpiIdentifier.name}"))
        return streamReader.getCPIStream(cpiIdentifier, fileHash)
    }

    override val isRunning: Boolean
        get() {
            return !stopped
        }

    override fun start() {
        lock.withLock {
            if (stopped) {
                cpiListHandler.start()
                streamReader.start()
                stopped = false
            }
        }
    }

    override fun stop() {
        lock.withLock {
            if (!stopped) {
                streamReader.stop()
                cpiListHandler.stop()
                stopped = true
            }
        }
    }
}

