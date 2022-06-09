package net.corda.p2p.gateway.messaging.internal

import net.corda.configuration.read.ConfigurationReadService
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.ConfigurationChangeHandler
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.lifecycle.domino.logic.util.ResourcesHolder
import net.corda.p2p.gateway.Gateway
import net.corda.p2p.gateway.messaging.ConnectionConfiguration
import net.corda.p2p.gateway.messaging.GatewayConfiguration
import net.corda.p2p.gateway.messaging.toGatewayConfiguration
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture

internal class ConnectionConfigReader(
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    private val configurationReaderService: ConfigurationReadService
): LifecycleWithDominoTile {

    companion object {
        private val logger = LoggerFactory.getLogger(ConnectionConfigReader::class.java)
    }

    var connectionConfig = ConnectionConfiguration()

    override val dominoTile = ComplexDominoTile(
        this::class.java.simpleName,
        lifecycleCoordinatorFactory,
        configurationChangeHandler = ConfigChangeHandler()
    )

    private inner class ConfigChangeHandler: ConfigurationChangeHandler<GatewayConfiguration>(
        configurationReaderService,
        Gateway.CONFIG_KEY,
        { it.toGatewayConfiguration() }
    ) {
        override fun applyNewConfiguration(
            newConfiguration: GatewayConfiguration,
            oldConfiguration: GatewayConfiguration?,
            resources: ResourcesHolder
        ): CompletableFuture<Unit> {
            if (newConfiguration.connectionConfig != oldConfiguration?.connectionConfig) {
                logger.info("New configuration, connection settings updated to ${newConfiguration.connectionConfig}.")
                connectionConfig = newConfiguration.connectionConfig
            }
            return CompletableFuture.completedFuture(Unit)
        }

    }
}