package net.corda.cpi.read.impl.kafka

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import net.corda.cpi.read.CPIRead
import net.corda.cpi.read.CPIListener
import net.corda.cpi.read.CPIMetadataListener
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.packaging.Cpb
import org.osgi.service.component.annotations.Component
import java.io.InputStream
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@Component(service = [CPIRead::class])
class CPIReadImplKafka(
    subscriptionFactory: SubscriptionFactory,
    nodeConfig: Config = ConfigFactory.empty()
) : CPIRead {


    private val lock = ReentrantLock()
    companion object {
        // TODO: Move the below 2 to writer?
        const val CPILIST_READ = "CPILIST_READ"
        const val CPIMETADATA_READ = "CPIMETADATA_READ"
        const val CPILIST_TOPICNAME = "CPIIDENTIFIERLIST"
        const val CPILIST_KEYNAME = "CPIIDENTIFIERLISTKEY"
        const val CPIMETADATA_TOPICNAME = "CPIMETADATA"
    }

    @Volatile
    private var stopped = true

    private val cpiListHandler: CPIListHandler = CPIListHandler(subscriptionFactory, nodeConfig)

    override fun registerCallback(cpiListener: CPIListener): AutoCloseable {
        return cpiListHandler.registerCPIListCallback(cpiListener)
    }

    override fun getCPI(cpbIdentifier: Cpb.Identifier): InputStream {
        TODO("Not yet implemented")
    }

    override val isRunning: Boolean
        get() {
            return !stopped
        }

    override fun start() {
        lock.withLock {
            cpiListHandler.start()
            stopped = false
        }
    }

    override fun stop() {
        lock.withLock {
            cpiListHandler.stop()
            stopped = true
        }
    }
}

