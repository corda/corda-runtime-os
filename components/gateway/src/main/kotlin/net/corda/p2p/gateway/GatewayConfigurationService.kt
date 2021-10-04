package net.corda.p2p.gateway

import com.typesafe.config.Config
import net.corda.configuration.read.ConfigurationHandler
import net.corda.configuration.read.ConfigurationReadService
import net.corda.p2p.gateway.domino.DominoTile
import net.corda.p2p.gateway.domino.LeafDominoTile
import net.corda.p2p.gateway.messaging.ConnectionConfiguration
import net.corda.p2p.gateway.messaging.GatewayConfiguration
import net.corda.p2p.gateway.messaging.RevocationConfig
import net.corda.p2p.gateway.messaging.RevocationConfigMode
import net.corda.p2p.gateway.messaging.SslConfiguration
import net.corda.v5.base.util.base64ToByteArray
import net.corda.v5.base.util.contextLogger
import java.util.concurrent.atomic.AtomicReference

class GatewayConfigurationService(
    parent: DominoTile,
    private val configurationReaderService: ConfigurationReadService,
    private val listener: ReconfigurationListener,
) : LeafDominoTile(parent),
    ConfigurationHandler {

    companion object {
        const val CONFIG_KEY = "p2p.gateway"
        private val logger = contextLogger()
    }

    interface ReconfigurationListener {
        fun gotNewConfiguration(newConfiguration: GatewayConfiguration, oldConfiguration: GatewayConfiguration)
    }

    private val configurationHolder = AtomicReference<GatewayConfiguration>()

    private class ConfigurationError(msg: String) : Exception(msg)

    @Suppress("TooGenericExceptionCaught")
    override fun onNewConfiguration(changedKeys: Set<String>, config: Map<String, Config>) {
        if (changedKeys.contains(CONFIG_KEY)) {
            try {
                applyNewConfiguration(config[CONFIG_KEY])
            } catch (e: Throwable) {
                gotError(e)
            }
        }
    }

    private fun applyNewConfiguration(newConfiguration: Config?) {
        val configuration = toGatewayConfig(newConfiguration)
        logger.info("Got for $name Gateway configuration ${configuration.hostAddress}:${configuration.hostPort}")
        val oldConfiguration = configurationHolder.getAndSet(configuration)
        if (oldConfiguration == configuration) {
            logger.info("Configuration of $name had not changed")
            return
        }
        if ((state != State.Created) && (oldConfiguration != null)) {
            logger.info("Reconfiguring gateway")
            listener.gotNewConfiguration(configuration, oldConfiguration)
            logger.info("Gateway reconfigured")
        }
        if ((state == State.Created) || (state == State.Error)) {
            state = State.Running
        }
    }

    private fun toSslConfig(config: Config): SslConfiguration {
        val revocationCheckMode = config.getEnum(RevocationConfigMode::class.java, "revocationCheck.mode")
        return SslConfiguration(
            rawKeyStore = config.getString("keyStore").base64ToByteArray(),
            keyStorePassword = config.getString("keyStorePassword"),
            rawTrustStore = config.getString("trustStore").base64ToByteArray(),
            trustStorePassword = config.getString("trustStorePassword"),
            revocationCheck = RevocationConfig(revocationCheckMode)
        )
    }
    private fun toConnectionConfig(config: Config): ConnectionConfiguration {
        return ConnectionConfiguration(
            maxClientConnections = config.getLong("maxClientConnections"),
            acquireTimeout = config.getDuration("acquireTimeout"),
            connectionIdleTimeout = config.getDuration("connectionIdleTimeout"),
            responseTimeout = config.getDuration("responseTimeout"),
            retryDelay = config.getDuration("retryDelay"),
        )
    }

    private fun toGatewayConfig(config: Config?): GatewayConfiguration {
        if (config == null) {
            throw ConfigurationError("Gateway configuration was removed!")
        }
        val connectionConfig = if (config.hasPath("connectionConfig")) {
            toConnectionConfig(config.getConfig("connectionConfig"))
        } else {
            ConnectionConfiguration()
        }
        return GatewayConfiguration(
            hostAddress = config.getString("hostAddress"),
            hostPort = config.getInt("hostPort"),
            sslConfig = toSslConfig(config.getConfig("sslConfig")),
            connectionConfig = connectionConfig,
            traceLogging = config.getBoolean("traceLogging")
        )
    }

    val configuration: GatewayConfiguration
        get() {
            return configurationHolder.get() ?: throw IllegalStateException("Configuration is not ready")
        }

    override fun startSequence() {
        if (state == State.Created) {
            configurationReaderService.registerForUpdates(this).also {
                executeBeforeClose(it::close)
            }
        }
        if (configurationHolder.get() != null) {
            state = State.Running
        }
    }
}
