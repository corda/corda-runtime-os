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

    // Map from the server configuration to a server
    private var httpServers = ConcurrentHashMap<GatewayServerConfiguration, HttpServer>()

    override val dominoTile = ComplexDominoTile(
        this::class.java.simpleName,
        lifecycleCoordinatorFactory,
        configurationChangeHandler = ReconfigurableHttpServerConfigChangeHandler(),
        dependentChildren = listOf(commonComponents.dominoTile.coordinatorName)
    )

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
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
            resources.keep {
                httpServers.values.forEach {
                    it.close()
                }
                httpServers.clear()
            }
            @Suppress("TooGenericExceptionCaught")
            try {
                val newServersConfiguration = newConfiguration.servers
                if (newServersConfiguration.isEmpty()) {
                    throw CordaRuntimeException("No servers defined!")
                }
                newServersConfiguration.groupBy {
                    it.hostAddress to it.hostPort
                }.forEach { (address, configurations) ->
                    if (configurations.size != 1) {
                        throw CordaRuntimeException("Can not have more than one server listening to: $address")
                    }
                }
                val mutualTlsTrustManager = createTrustManagerIfNeeded(
                    newConfiguration.sslConfig,
                    commonComponents.trustStoresMap,
                    dynamicCertificateSubjectStore,
                )
                val serversToStop = httpServers.filterKeys { configuration ->
                    !newServersConfiguration.contains(configuration)
                }
                httpServers.keys -= serversToStop.keys
                serversToStop.values.forEach {
                    it.close()
                }
                newServersConfiguration.forEach { configuration ->
                    httpServers.compute(configuration) { _, oldServer ->
                        oldServer?.close()
                        logger.info(
                            "New server configuration, ${dominoTile.coordinatorName} will be connected to " +
                                "${configuration.hostAddress}:${configuration.hostPort}${configuration.urlPath}"
                        )
                        HttpServer(
                            listener,
                            newConfiguration.maxRequestSize,
                            configuration,
                            commonComponents.dynamicKeyStore.serverKeyStore,
                            mutualTlsTrustManager,
                        ).also {
                            it.start()
                        }
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
