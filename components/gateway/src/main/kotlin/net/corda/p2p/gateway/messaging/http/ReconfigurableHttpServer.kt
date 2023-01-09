package net.corda.p2p.gateway.messaging.http

import io.netty.handler.codec.http.HttpResponseStatus
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.client.CryptoOpsClient
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.ConfigurationChangeHandler
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.lifecycle.domino.logic.util.ResourcesHolder
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.gateway.messaging.DynamicKeyStore
import net.corda.p2p.gateway.messaging.GatewayConfiguration
import net.corda.p2p.gateway.messaging.toGatewayConfiguration
import net.corda.schema.configuration.ConfigKeys
import net.corda.v5.base.util.contextLogger
import java.net.SocketAddress
import java.util.concurrent.CompletableFuture
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

@Suppress("LongParameterList")
class ReconfigurableHttpServer(
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    private val configurationReaderService: ConfigurationReadService,
    private val listener: HttpServerListener,
    subscriptionFactory: SubscriptionFactory,
    nodeConfiguration: SmartConfig,
    cryptoOpsClient: CryptoOpsClient
) : LifecycleWithDominoTile {

    @Volatile
    private var httpServer: HttpServer? = null
    private val serverLock = ReentrantReadWriteLock()

    private val dynamicKeyStore = DynamicKeyStore(
        lifecycleCoordinatorFactory,
        subscriptionFactory,
        nodeConfiguration,
        cryptoOpsClient
    )

    override val dominoTile = ComplexDominoTile(
        this::class.java.simpleName,
        lifecycleCoordinatorFactory,
        configurationChangeHandler = ReconfigurableHttpServerConfigChangeHandler(),
        dependentChildren = listOf(dynamicKeyStore.dominoTile.coordinatorName),
        managedChildren = listOf(dynamicKeyStore.dominoTile.toNamedLifecycle()),
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
                if (newConfiguration.hostPort == oldConfiguration?.hostPort) {
                    logger.info(
                        "New server configuration for ${dominoTile.coordinatorName} on the same port, " +
                            "HTTP server will have to go down"
                    )
                    serverLock.write {
                        val oldServer = httpServer
                        httpServer = null
                        oldServer?.close()
                        val newServer = HttpServer(
                            listener,
                            newConfiguration,
                            dynamicKeyStore.serverKeyStore
                        )
                        newServer.start()
                        resources.keep(newServer)
                        httpServer = newServer
                    }
                } else {
                    logger.info(
                        "New server configuration, ${dominoTile.coordinatorName} will be connected to " +
                            "${newConfiguration.hostAddress}:${newConfiguration.hostPort}"
                    )
                    val newServer = HttpServer(
                        listener,
                        newConfiguration,
                        dynamicKeyStore.serverKeyStore
                    )
                    newServer.start()
                    resources.keep(newServer)
                    serverLock.write {
                        val oldServer = httpServer
                        httpServer = null
                        oldServer?.close()
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
