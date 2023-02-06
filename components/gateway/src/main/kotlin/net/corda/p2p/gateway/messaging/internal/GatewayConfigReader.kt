package net.corda.p2p.gateway.messaging.internal

import net.corda.configuration.read.ConfigurationReadService
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.ConfigurationChangeHandler
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.lifecycle.domino.logic.util.ResourcesHolder
import net.corda.p2p.gateway.messaging.ConnectionConfiguration
import net.corda.p2p.gateway.messaging.GatewayConfiguration
import net.corda.p2p.gateway.messaging.toGatewayConfiguration
import net.corda.schema.configuration.ConfigKeys
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture

internal class GatewayConfigReader(
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    private val configurationReaderService: ConfigurationReadService
): LifecycleWithDominoTile {

    companion object {
        private val logger = LoggerFactory.getLogger(GatewayConfigReader::class.java)
    }

    private var gatewayConfiguration : GatewayConfiguration? = null

    val connectionConfig
        get() =
            gatewayConfiguration?.connectionConfig ?: ConnectionConfiguration()

    val sslConfiguration
        get() =
            gatewayConfiguration?.sslConfig

    override val dominoTile = ComplexDominoTile(
        this::class.java.simpleName,
        lifecycleCoordinatorFactory,
        configurationChangeHandler = ConfigChangeHandler()
    )

    private inner class ConfigChangeHandler: ConfigurationChangeHandler<GatewayConfiguration>(
        configurationReaderService,
        ConfigKeys.P2P_GATEWAY_CONFIG,
        { it.toGatewayConfiguration() }
    ) {
        override fun applyNewConfiguration(
            newConfiguration: GatewayConfiguration,
            oldConfiguration: GatewayConfiguration?,
            resources: ResourcesHolder
        ): CompletableFuture<Unit> {
            if (newConfiguration != oldConfiguration) {
                logger.info("New configuration, connection settings updated to ${newConfiguration.connectionConfig}.")
                gatewayConfiguration = newConfiguration
            }
            return CompletableFuture.completedFuture(Unit)
        }
    }
}