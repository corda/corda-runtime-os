package net.corda.p2p.gateway.messaging

import net.corda.configuration.read.ConfigurationReadService
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.ConfigurationAwareLeafTile
import net.corda.p2p.gateway.Gateway.Companion.CONFIG_KEY
import net.corda.p2p.gateway.messaging.http.DestinationInfo
import net.corda.p2p.gateway.messaging.http.HttpClient
import net.corda.p2p.gateway.messaging.http.HttpEventListener
import net.corda.v5.base.util.contextLogger
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class ReconfigurableConnectionManager(
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    configurationReaderService: ConfigurationReadService,
    listener: HttpEventListener,
    private val managerLock: ReentrantReadWriteLock = ReentrantReadWriteLock(),
    private val managerFactory: (SslConfiguration) -> ConnectionManager = { ConnectionManager(it, listener) }
) : ConfigurationAwareLeafTile<GatewayConfiguration>(
    lifecycleCoordinatorFactory,
    configurationReaderService,
    CONFIG_KEY,
    { it.toGatewayConfiguration() },
) {
    @Volatile
    private var manager: ConnectionManager? = null

    companion object {
        private val logger = contextLogger()
    }

    fun acquire(destinationInfo: DestinationInfo): HttpClient {
        return dataAccess {
            if (manager == null) {
                throw IllegalStateException("Manager is not ready")
            }

            managerLock.read {
                manager!!.acquire(destinationInfo)
            }
        }
    }

    override fun applyNewConfiguration(
        newConfiguration: GatewayConfiguration,
        oldConfiguration: GatewayConfiguration?,
    ) {
        if (newConfiguration.sslConfig != oldConfiguration?.sslConfig) {
            logger.info("New SSL configuration, clients for $name will be reconnected")
            val newManager = managerFactory(newConfiguration.sslConfig)
            resources.keep(newManager)
            managerLock.write {
                val oldManager = manager
                manager = null
                oldManager?.close()
                manager = newManager
            }
        }
    }
}
