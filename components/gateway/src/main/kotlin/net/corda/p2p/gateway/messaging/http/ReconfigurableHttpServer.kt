package net.corda.p2p.gateway.messaging.http

import io.netty.handler.codec.http.HttpResponseStatus
import net.corda.configuration.read.ConfigurationReadService
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.ConfigurationAwareLeafTile
import net.corda.p2p.gateway.Gateway
import net.corda.p2p.gateway.messaging.GatewayConfiguration
import net.corda.p2p.gateway.messaging.toGatewayConfiguration
import net.corda.v5.base.util.contextLogger
import java.net.SocketAddress
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class ReconfigurableHttpServer(
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    configurationReaderService: ConfigurationReadService,
    private val listener: HttpServerListener,
) :
    ConfigurationAwareLeafTile<GatewayConfiguration>(
        lifecycleCoordinatorFactory,
        configurationReaderService,
        Gateway.CONFIG_KEY,
        { it.toGatewayConfiguration() }
    ) {

    @Volatile
    private var httpServer: HttpServer? = null
    private val serverLock = ReentrantReadWriteLock()

    companion object {
        private val logger = contextLogger()
    }

    fun writeResponse(status: HttpResponseStatus, address: SocketAddress, payload: ByteArray = ByteArray(0)) {
        serverLock.read {
            val server = httpServer ?: throw IllegalStateException("Server is not ready")
            server.write(status, payload, address)
        }
    }

    override fun applyNewConfiguration(newConfiguration: GatewayConfiguration, oldConfiguration: GatewayConfiguration?) {
        if (newConfiguration.hostPort == oldConfiguration?.hostPort) {
            logger.info("New server configuration for $name on the same port, HTTP server will have to go down")
            serverLock.write {
                val oldServer = httpServer
                httpServer = null
                oldServer?.stop()
                val newServer = HttpServer(listener, newConfiguration)
                newServer.start()
                resources.keep(newServer::stop)
                httpServer = newServer
            }
        } else {
            logger.info("New server configuration, $name will be connected to ${newConfiguration.hostAddress}:${newConfiguration.hostPort}")
            val newServer = HttpServer(listener, newConfiguration)
            newServer.start()
            resources.keep(newServer::stop)
            serverLock.write {
                val oldServer = httpServer
                httpServer = null
                oldServer?.stop()
                httpServer = newServer
            }
        }
    }
}
