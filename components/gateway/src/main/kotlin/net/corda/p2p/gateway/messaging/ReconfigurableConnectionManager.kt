package net.corda.p2p.gateway.messaging

import net.corda.configuration.read.ConfigurationReadService
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.ConfigurationChangeHandler
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.lifecycle.domino.logic.util.ResourcesHolder
import net.corda.p2p.gateway.messaging.http.DestinationInfo
import net.corda.p2p.gateway.messaging.http.HttpClient
import net.corda.schema.configuration.ConfigKeys
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture

class ReconfigurableConnectionManager(
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    val configurationReaderService: ConfigurationReadService,
    private val managerFactory: (sslConfig: SslConfiguration, connectionConfig: ConnectionConfiguration) -> ConnectionManager =
        { sslConfig, connectionConfig -> ConnectionManager(sslConfig, connectionConfig) }
) : LifecycleWithDominoTile {

    override val dominoTile = ComplexDominoTile(
        this::class.java.simpleName,
        lifecycleCoordinatorFactory,
        configurationChangeHandler = ConnectionManagerConfigChangeHandler()
    )

    @Volatile
    private var manager: ConnectionManager? = null

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
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
        ConfigKeys.P2P_GATEWAY_CONFIG,
        { it.toGatewayConfiguration() }
    ) {
        override fun applyNewConfiguration(
            newConfiguration: GatewayConfiguration,
            oldConfiguration: GatewayConfiguration?,
            resources: ResourcesHolder,
        ): CompletableFuture<Unit> {
            val configUpdateResult = CompletableFuture<Unit>()
            @Suppress("TooGenericExceptionCaught")
            try {
                if (newConfiguration.sslConfig != oldConfiguration?.sslConfig ||
                    newConfiguration.connectionConfig != oldConfiguration.connectionConfig
                ) {
                    logger.info("New configuration, clients for ${dominoTile.coordinatorName} will be reconnected")
                    val newManager = managerFactory(newConfiguration.sslConfig, newConfiguration.connectionConfig)
                    resources.keep(newManager)
                    val oldManager = manager
                    manager = null
                    oldManager?.close()
                    manager = newManager
                }
                configUpdateResult.complete(Unit)
            } catch (e: Throwable) {
                configUpdateResult.completeExceptionally(e)
            }
            return configUpdateResult
        }
    }
}
