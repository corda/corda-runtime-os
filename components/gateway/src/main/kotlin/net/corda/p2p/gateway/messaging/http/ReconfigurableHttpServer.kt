package net.corda.p2p.gateway.messaging.http

import net.corda.configuration.read.ConfigurationReadService
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.ConfigurationChangeHandler
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.lifecycle.domino.logic.util.ResourcesHolder
import net.corda.p2p.gateway.messaging.GatewayConfiguration
import net.corda.p2p.gateway.messaging.GatewayServerConfiguration
import net.corda.p2p.gateway.messaging.http.DynamicX509ExtendedTrustManager.Companion.createTrustManagerIfNeeded
import net.corda.p2p.gateway.messaging.internal.CommonComponents
import net.corda.p2p.gateway.messaging.internal.RequestListener
import net.corda.p2p.gateway.messaging.mtls.DynamicCertificateSubjectStore
import net.corda.p2p.gateway.messaging.toGatewayConfiguration
import net.corda.schema.configuration.ConfigKeys
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.slf4j.LoggerFactory
import java.net.BindException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

@Suppress("LongParameterList")
internal class ReconfigurableHttpServer(
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    private val configurationReaderService: ConfigurationReadService,
    private val listener: RequestListener,
    private val commonComponents: CommonComponents,
    private val dynamicCertificateSubjectStore: DynamicCertificateSubjectStore,
) : LifecycleWithDominoTile {

    private data class ServerKey(
        val hostAddress: String,
        val hostPort: Int,
    )

    // Map from the server configuration to a server
    private val httpServers = ConcurrentHashMap<ServerKey, HttpServer>()

    override val dominoTile = ComplexDominoTile(
        this::class.java.simpleName,
        lifecycleCoordinatorFactory,
        configurationChangeHandler = ReconfigurableHttpServerConfigChangeHandler(),
        dependentChildren = listOf(commonComponents.dominoTile.coordinatorName),
    )

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    inner class ReconfigurableHttpServerConfigChangeHandler : ConfigurationChangeHandler<GatewayConfiguration>(
        configurationReaderService,
        ConfigKeys.P2P_GATEWAY_CONFIG,
        { it.toGatewayConfiguration() },
    ) {
        override fun applyNewConfiguration(
            newConfiguration: GatewayConfiguration,
            oldConfiguration: GatewayConfiguration?,
            resources: ResourcesHolder,
        ): CompletableFuture<Unit> {
            val configUpdateResult = CompletableFuture<Unit>()
            resources.keep {
                httpServers.values.forEach {
                    it.close()
                }
                httpServers.clear()
            }
            @Suppress("TooGenericExceptionCaught")
            try {
                val newServersConfiguration = newConfiguration.serversConfiguration.groupingBy {
                    ServerKey(it.hostAddress, it.hostPort)
                }.fold(
                    initialValueSelector = { key, _ ->
                        GatewayServerConfiguration(
                            hostAddress = key.hostAddress,
                            hostPort = key.hostPort,
                            urlPaths = emptySet()
                        )
                    },
                    operation = { _, soFar, config ->
                        config.copy(
                            urlPaths = soFar.urlPaths + config.urlPaths
                        )
                    }
                )
                if (newServersConfiguration.isEmpty()) {
                    throw IllegalArgumentException("No servers defined!")
                }
                val mutualTlsTrustManager = createTrustManagerIfNeeded(
                    newConfiguration.sslConfig,
                    commonComponents.trustStoresMap,
                    dynamicCertificateSubjectStore,
                )
                val serversToStop = httpServers.filterKeys { configuration ->
                    !newServersConfiguration.containsKey(configuration)
                }
                httpServers.keys -= serversToStop.keys
                try {
                    newServersConfiguration.forEach { (key, serverConfiguration) ->
                        httpServers.compute(key) { _, oldServer ->
                            oldServer?.close()
                            serverConfiguration.urlPaths.forEach { urlPath ->
                                logger.info(
                                    "New server configuration, ${dominoTile.coordinatorName} will be connected to " +
                                            "${serverConfiguration.hostAddress}:${serverConfiguration.hostPort}$urlPath",
                                )
                            }
                            HttpServer(
                                listener,
                                newConfiguration.maxRequestSize,
                                serverConfiguration,
                                commonComponents.dynamicKeyStore.serverKeyStore,
                                mutualTlsTrustManager,
                            ).also {
                                try {
                                    it.start()
                                } catch (e: BindException) {
                                    throw CordaRuntimeException(
                                        "Failed to connect on " +
                                                "'${serverConfiguration.hostAddress}:${serverConfiguration.hostPort}' " +
                                                "address. Please make sure the required address is not in use " +
                                                "and could be accessed.", e
                                    )
                                }
                            }
                        }
                    }
                } finally {
                    serversToStop.values.forEach {
                        it.close()
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
