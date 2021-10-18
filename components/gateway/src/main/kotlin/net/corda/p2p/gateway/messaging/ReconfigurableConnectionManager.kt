package net.corda.p2p.gateway.messaging

import net.corda.configuration.read.ConfigurationReadService
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.ConfigurationAwareTile
import net.corda.p2p.gateway.Gateway.Companion.CONFIG_KEY
import net.corda.p2p.gateway.messaging.http.DestinationInfo
import net.corda.p2p.gateway.messaging.http.HttpClient
import net.corda.p2p.gateway.messaging.http.HttpEventListener
import net.corda.v5.base.util.contextLogger

class ReconfigurableConnectionManager(
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    configurationReaderService: ConfigurationReadService,
    listener: HttpEventListener,
    private val managerFactory: (SslConfiguration) -> ConnectionManager = { ConnectionManager(it, listener) }
) : ConfigurationAwareTile<GatewayConfiguration>(
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
        return withLifecycleLock {
            if (manager == null) {
                throw IllegalStateException("Manager is not ready")
            }

            manager!!.acquire(destinationInfo)
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
            val oldManager = manager
            manager = null
            oldManager?.close()
            manager = newManager
        }
    }
}
