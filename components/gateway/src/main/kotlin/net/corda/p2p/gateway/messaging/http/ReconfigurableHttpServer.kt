package net.corda.p2p.gateway.messaging.http

import io.netty.handler.codec.http.HttpResponseStatus
import net.corda.configuration.read.ConfigurationReadService
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.ConfigurationChangeHandler
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.lifecycle.domino.logic.util.ResourcesHolder
import net.corda.p2p.gateway.Gateway
import net.corda.p2p.gateway.messaging.GatewayConfiguration
import net.corda.p2p.gateway.messaging.toGatewayConfiguration
import net.corda.v5.base.util.contextLogger
import java.net.SocketAddress
import java.util.concurrent.CompletableFuture
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class ReconfigurableHttpServer(
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    private val configurationReaderService: ConfigurationReadService,
    private val listener: HttpServerListener,
) : LifecycleWithDominoTile {

    @Volatile
    private var httpServer: HttpServer? = null
    private val serverLock = ReentrantReadWriteLock()

    override val complexDominoTile = ComplexDominoTile(
        this::class.java.simpleName,
        lifecycleCoordinatorFactory,
        configurationChangeHandler = ReconfigurableHttpServerConfigChangeHandler()
    )

    companion object {
        private val logger = contextLogger()
    }

    fun writeResponse(status: HttpResponseStatus, address: SocketAddress, payload: ByteArray = ByteArray(0)) {
        serverLock.read {
            val server = httpServer ?: throw IllegalStateException("Server is not ready")
            server.write(status, payload, address)
        }
    }

    inner class ReconfigurableHttpServerConfigChangeHandler : ConfigurationChangeHandler<GatewayConfiguration>(
        configurationReaderService,
        Gateway.CONFIG_KEY,
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
                if (newConfiguration.hostPort == oldConfiguration?.hostPort) {
                    logger.info("New server configuration for ${complexDominoTile.coordinatorName} on the same port, HTTP server will have to go down")
                    serverLock.write {
                        val oldServer = httpServer
                        httpServer = null
                        oldServer?.stop()
                        val newServer = HttpServer(listener, newConfiguration)
                        newServer.start()
                        resources.keep(newServer)
                        httpServer = newServer
                    }
                } else {
                    logger.info("New server configuration, ${complexDominoTile.coordinatorName} will be connected to " +
                        "${newConfiguration.hostAddress}:${newConfiguration.hostPort}")
                    val newServer = HttpServer(listener, newConfiguration)
                    newServer.start()
                    resources.keep(newServer)
                    serverLock.write {
                        val oldServer = httpServer
                        httpServer = null
                        oldServer?.stop()
                        httpServer = newServer
                    }
                }
                configUpdateResult.complete(Unit)
            } catch (e: Throwable) {
                configUpdateResult.completeExceptionally(e)
            }
            return configUpdateResult
        }
    }
}
