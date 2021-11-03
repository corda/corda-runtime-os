package net.corda.p2p.gateway.messaging

import net.corda.configuration.read.ConfigurationReadService
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.ConfigurationAwareLeafTile
import net.corda.p2p.gateway.Gateway.Companion.CONFIG_KEY
import net.corda.p2p.gateway.messaging.http.DestinationInfo
import net.corda.p2p.gateway.messaging.http.HttpClient
import net.corda.v5.base.util.contextLogger

class ReconfigurableConnectionManager(
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    configurationReaderService: ConfigurationReadService,
    private val managerFactory: (sslConfig: SslConfiguration, connectionConfig: ConnectionConfiguration) -> ConnectionManager =
        { sslConfig, connectionConfig -> ConnectionManager(sslConfig, connectionConfig) }
) : ConfigurationAwareLeafTile<GatewayConfiguration>(
    lifecycleCoordinatorFactory,
    configurationReaderService,
    CONFIG_KEY,
    { it.toGatewayConfiguration() },
) {
    @Volatile
    private var manager: ConnectionManager? = null

    // When the updated domino logic (supporting internal tile with configuration) is in place, this will be updated. See: CORE-2876.
    @Volatile
    var latestConnectionConfig = ConnectionConfiguration()

    fun latestConnectionConfig() = latestConnectionConfig

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
        if (newConfiguration.sslConfig != oldConfiguration?.sslConfig ||
            newConfiguration.connectionConfig != oldConfiguration.connectionConfig) {
            logger.info("New configuration, clients for $name will be reconnected")
            latestConnectionConfig = newConfiguration.connectionConfig
            val newManager = managerFactory(newConfiguration.sslConfig, newConfiguration.connectionConfig)
            resources.keep(newManager)
            val oldManager = manager
            manager = null
            oldManager?.close()
            manager = newManager
        }
    }
}
