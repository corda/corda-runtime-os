package net.corda.p2p.gateway.domino

import com.typesafe.config.Config
import net.corda.configuration.read.ConfigurationHandler
import net.corda.configuration.read.ConfigurationReadService
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.p2p.gateway.messaging.ConnectionConfiguration
import net.corda.p2p.gateway.messaging.GatewayConfiguration
import net.corda.p2p.gateway.messaging.RevocationConfig
import net.corda.p2p.gateway.messaging.RevocationConfigMode
import net.corda.p2p.gateway.messaging.SslConfiguration
import net.corda.v5.base.util.base64ToByteArray
import net.corda.v5.base.util.contextLogger
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

abstract class ConfigurationAwareTile(
    coordinatorFactory: LifecycleCoordinatorFactory,
    configurationReaderService: ConfigurationReadService,
) :
    LeafTile(coordinatorFactory),
    ConfigurationHandler {

    companion object {
        const val CONFIG_KEY = "p2p.gateway"
        private val logger = contextLogger()
    }

    private var configurationHolder = AtomicReference<GatewayConfiguration>()

    private val canReceiveConfigurations = AtomicBoolean(false)
    private val registration = configurationReaderService.registerForUpdates(this)

    override fun close() {
        registration.close()
        super.close()
    }

    override fun onNewConfiguration(changedKeys: Set<String>, config: Map<String, Config>) {
        if (changedKeys.contains(CONFIG_KEY)) {
            val newConfiguration = config[CONFIG_KEY]
            if (newConfiguration != null) {
                applyNewConfiguration(newConfiguration)
            }
        }
    }

    private fun applyNewConfiguration(newConfiguration: Config) {
        val configuration = toGatewayConfig(newConfiguration)
        logger.info("Got for Gateway configuration ${configuration.hostAddress}:${configuration.hostPort}")
        val oldConfiguration = configurationHolder.getAndSet(configuration)
        if (oldConfiguration == configuration) {
            logger.info("Configuration had not changed")
            return
        } else if (canReceiveConfigurations.get()) {
            logger.info("Reconfiguring gateway $name")
            @Suppress("TooGenericExceptionCaught")
            try {
                applyNewConfiguration(configuration, oldConfiguration)
                updateState(State.Started)
                logger.info("Gateway reconfigured $name")
            } catch (e: Throwable) {
                gotError(e)
            }
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

    private fun toGatewayConfig(config: Config): GatewayConfiguration {
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

    abstract fun applyNewConfiguration(newConfiguration: GatewayConfiguration, oldConfiguration: GatewayConfiguration?)

    override fun createResources() {
        if (configurationHolder.get() != null) {
            @Suppress("TooGenericExceptionCaught")
            try {
                applyNewConfiguration(configurationHolder.get(), null)
                updateState(State.Started)
            } catch (e: Throwable) {
                gotError(e)
            }
        }
        canReceiveConfigurations.set(true)
        executeBeforeStop {
            canReceiveConfigurations.set(false)
        }
    }
}
