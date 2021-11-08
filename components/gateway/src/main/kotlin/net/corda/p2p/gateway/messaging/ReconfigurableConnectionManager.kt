package net.corda.p2p.gateway.messaging

import net.corda.configuration.read.ConfigurationReadService
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.ConfigurationChangeHandler
import net.corda.lifecycle.domino.logic.DominoTile
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.lifecycle.domino.logic.util.ResourcesHolder
import net.corda.p2p.gateway.Gateway.Companion.CONFIG_KEY
import net.corda.p2p.gateway.messaging.http.DestinationInfo
import net.corda.p2p.gateway.messaging.http.HttpClient
import net.corda.v5.base.util.contextLogger

class ReconfigurableConnectionManager(
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    val configurationReaderService: ConfigurationReadService,
    private val managerFactory: (sslConfig: SslConfiguration, connectionConfig: ConnectionConfiguration) -> ConnectionManager =
    { sslConfig, connectionConfig -> ConnectionManager(sslConfig, connectionConfig) }
) : LifecycleWithDominoTile {

    override val dominoTile = DominoTile(
        this::class.java.simpleName,
        lifecycleCoordinatorFactory,
        configurationChangeHandler = ConnectionManagerConfigChangeHandler()
    )

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
        return dominoTile.withLifecycleLock {
            if (manager == null) {
                throw IllegalStateException("Manager is not ready")
            }
            manager!!.acquire(destinationInfo)
        }
    }

    inner class ConnectionManagerConfigChangeHandler : ConfigurationChangeHandler<GatewayConfiguration>(
        configurationReaderService,
        CONFIG_KEY,
        {it.toGatewayConfiguration()}
    ) {
        override fun applyNewConfiguration(
            newConfiguration: GatewayConfiguration,
            oldConfiguration: GatewayConfiguration?,
            resources: ResourcesHolder
        ) {
            @Suppress("TooGenericExceptionCaught")
            try {
                if (newConfiguration.sslConfig != oldConfiguration?.sslConfig ||
                    newConfiguration.connectionConfig != oldConfiguration.connectionConfig) {
                    logger.info("New configuration, clients for ${dominoTile.name} will be reconnected")
                    latestConnectionConfig = newConfiguration.connectionConfig
                    val newManager = managerFactory(newConfiguration.sslConfig, newConfiguration.connectionConfig)
                    resources.keep(newManager)
                    val oldManager = manager
                    manager = null
                    oldManager?.close()
                    manager = newManager
                    this@ReconfigurableConnectionManager.dominoTile.configApplied(DominoTile.ConfigUpdateResult.Success)
                }
            } catch (e: Throwable) {
                this@ReconfigurableConnectionManager.dominoTile.configApplied(DominoTile.ConfigUpdateResult.Error(e))
            }
        }
    }
}
