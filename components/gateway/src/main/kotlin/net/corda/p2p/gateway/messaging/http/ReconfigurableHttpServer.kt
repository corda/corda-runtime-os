package net.corda.p2p.gateway.messaging.http

import io.netty.handler.codec.http.HttpResponseStatus
import net.corda.configuration.read.ConfigurationReadService
import net.corda.p2p.gateway.GatewayConfigurationService
import net.corda.p2p.gateway.domino.LifecycleWithCoordinator
import net.corda.p2p.gateway.messaging.GatewayConfiguration
import net.corda.v5.base.util.contextLogger
import java.net.SocketAddress
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class ReconfigurableHttpServer(
    parent: LifecycleWithCoordinator,
    configurationReaderService: ConfigurationReadService,
    private val listener: HttpEventListener,
) :
    GatewayConfigurationService.ReconfigurationListener,
    LifecycleWithCoordinator(parent) {

    private val configurationService = GatewayConfigurationService(this, configurationReaderService, this)

    @Volatile
    private var httpServer: HttpServer? = null
    private val serverLock = ReentrantReadWriteLock()

    companion object {
        private val logger = contextLogger()
    }

    override fun startSequence() {
        if (httpServer?.isRunning != true) {
            serverLock.write {
                if (httpServer?.isRunning != true) {
                    logger.info(
                        "Starting HTTP server for $name to " +
                            "${configurationService.configuration.hostAddress}:${configurationService.configuration.hostPort}"
                    )
                    val newServer = HttpServer(listener, configurationService.configuration)
                    newServer.start()
                    executeBeforeStop(newServer::stop)
                    httpServer = newServer
                }
            }
        }

        logger.info("Started P2P message receiver")
        state = State.Started
    }

    override val children = listOf(configurationService)

    fun writeResponse(status: HttpResponseStatus, address: SocketAddress) {
        serverLock.read {
            val server = httpServer ?: throw IllegalStateException("Server is not ready")
            server.write(status, ByteArray(0), address)
        }
    }

    override fun gotNewConfiguration(newConfiguration: GatewayConfiguration, oldConfiguration: GatewayConfiguration) {
        if (newConfiguration.hostPort == oldConfiguration.hostPort) {
            logger.info("New server configuration for $name on the same port, HTTP server will have to go down")
            serverLock.write {
                val oldServer = httpServer
                httpServer = null
                oldServer?.stop()
                val newServer = HttpServer(listener, newConfiguration)
                newServer.start()
                executeBeforeStop(newServer::stop)
                httpServer = newServer
            }
        } else {
            logger.info("New server configuration, $name will be connected to ${newConfiguration.hostAddress}:${newConfiguration.hostPort}")
            val newServer = HttpServer(listener, newConfiguration)
            newServer.start()
            executeBeforeStop(newServer::stop)
            serverLock.write {
                val oldServer = httpServer
                httpServer = null
                oldServer?.stop()
                httpServer = newServer
            }
        }
    }
}
