package net.corda.p2p.gateway

import com.typesafe.config.Config
import net.corda.configuration.read.ConfigurationHandler
import net.corda.configuration.read.ConfigurationReadService
import net.corda.lifecycle.LifecycleEvent
import net.corda.p2p.gateway.domino.DominoCoordinatorFactory
import net.corda.p2p.gateway.domino.DominoTile
import net.corda.p2p.gateway.messaging.ConnectionConfiguration
import net.corda.p2p.gateway.messaging.GatewayConfiguration
import net.corda.p2p.gateway.messaging.RevocationConfig
import net.corda.p2p.gateway.messaging.RevocationConfigMode
import net.corda.p2p.gateway.messaging.SslConfiguration
import net.corda.v5.base.util.contextLogger
import java.io.ByteArrayInputStream
import java.security.KeyStore
import java.util.Base64

internal class GatewayConfigurationListener(
    private val readerService: ConfigurationReadService,
    coordinatorFactory: DominoCoordinatorFactory,
) : DominoTile(coordinatorFactory), ConfigurationHandler {
    companion object {
        const val CONFIG_KEY = "GATEWAY"
        private val logger = contextLogger()
    }
    private class ConfigurationError(msg: String) : Exception(msg)

    // YIFT: Handle changes to configuration
    class ConfigurationChanged(val configuration: GatewayConfiguration) : LifecycleEvent

    @Suppress("TooGenericExceptionCaught")
    override fun onNewConfiguration(changedKeys: Set<String>, config: Map<String, Config>) {
        if (changedKeys.contains(CONFIG_KEY)) {
            try {
                val configuration = toGatewayConfig(config[CONFIG_KEY])
                logger.info("Got new Gateway configuration $configuration")
                //coordinator.postEvent(ConfigurationChanged(configuration))
                setReady()
            } catch (e: Throwable) {
                gotError(e)
            }
        }
        // setReady()
    }

    private fun readKeyStore(config: Config, name: String): Pair<String, KeyStore> {
        val password = config.getString("${name}Password")
        val keyStore = KeyStore.getInstance("JKS").also {
            val base64KeyStore = config.getString(name)
            ByteArrayInputStream(Base64.getDecoder().decode(base64KeyStore)).use { keySoreInputStream ->
                it.load(keySoreInputStream, password.toCharArray())
            }
        }
        return password to keyStore
    }

    private fun toSslConfig(config: Config): SslConfiguration {
        val (keyStorePassword, keyStore) = readKeyStore(config, "keyStore")
        val (trustStorePassword, trustStore) = readKeyStore(config, "trustStore")
        val revocationCheckMode = config.getEnum(RevocationConfigMode::class.java, "revocationCheck.mode")
        return SslConfiguration(
            keyStore = keyStore,
            keyStorePassword = keyStorePassword,
            trustStore = trustStore,
            trustStorePassword = trustStorePassword,
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

    override fun prepareResources() {
        keepResources(readerService.registerForUpdates(this))
    }
    override fun startupSequenceCompleted() {
        // Do nothing, we will report ready when we get the correct configuration
    }
}
