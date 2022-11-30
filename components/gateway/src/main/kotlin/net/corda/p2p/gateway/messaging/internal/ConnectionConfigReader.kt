package net.corda.p2p.gateway.messaging.internal

import net.corda.configuration.read.ConfigurationReadService
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.ConfigurationChangeHandler
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.lifecycle.domino.logic.util.ResourcesHolder
import net.corda.p2p.gateway.messaging.ConnectionConfiguration
import net.corda.p2p.gateway.messaging.GatewayConfiguration
import net.corda.p2p.gateway.messaging.SslConfiguration
import net.corda.p2p.gateway.messaging.toGatewayConfiguration
import net.corda.schema.configuration.ConfigKeys
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture

internal class ConnectionConfigReader(
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    private val configurationReaderService: ConfigurationReadService
): LifecycleWithDominoTile {

    companion object {
        private val logger = LoggerFactory.getLogger(ConnectionConfigReader::class.java)
    }

    val connectionConfig: ConnectionConfiguration
        get() = configuration?.connectionConfig ?: ConnectionConfiguration()

    val sslConfiguration: SslConfiguration?
        get() = configuration?.sslConfig

    private var configuration: GatewayConfiguration? = null

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
                configuration = newConfiguration
            }
            return CompletableFuture.completedFuture(Unit)
        }
    }
}