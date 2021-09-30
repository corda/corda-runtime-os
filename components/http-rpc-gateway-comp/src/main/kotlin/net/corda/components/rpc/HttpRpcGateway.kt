package net.corda.components.rpc

import com.typesafe.config.Config
import net.corda.configuration.read.ConfigurationReadService
import net.corda.httprpc.security.read.RPCSecurityManager
import net.corda.httprpc.security.read.RPCSecurityManagerFactory
import net.corda.httprpc.server.HttpRpcServer
import net.corda.httprpc.server.config.models.HttpRpcContext
import net.corda.httprpc.server.config.models.HttpRpcSSLSettings
import net.corda.httprpc.server.config.models.HttpRpcSettings
import net.corda.httprpc.server.config.models.HttpRpcSettings.Companion.MAX_CONTENT_LENGTH_DEFAULT_VALUE
import net.corda.httprpc.server.factory.HttpRpcServerFactory
import net.corda.httprpc.ssl.SslCertReadService
import net.corda.httprpc.ssl.SslCertReadServiceFactory

import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleEvent
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.NetworkHostAndPort
import net.corda.v5.base.util.contextLogger
import net.corda.v5.httprpc.api.PluggableRPCOps
import net.corda.v5.httprpc.api.RpcOps
import org.slf4j.Logger

class ConfigReceivedEvent(val currentConfigurationSnapshot: Map<String, Config>) : LifecycleEvent
class MessagingConfigUpdateEvent(val currentConfigurationSnapshot: Map<String, Config>) : LifecycleEvent

@Suppress("LongParameterList")
class HttpRpcGateway(
    private val lifeCycleCoordinator: LifecycleCoordinator,
    private val configurationReadService: ConfigurationReadService,
    private val httpRpcServerFactory: HttpRpcServerFactory,
    private val rpcSecurityManagerFactory: RPCSecurityManagerFactory,
    private val sslCertReadServiceFactory: SslCertReadServiceFactory,
    private val rpcOps: List<PluggableRPCOps<out RpcOps>>
) : Lifecycle {

    companion object {
        private val log: Logger = contextLogger()
        const val MESSAGING_CONFIG: String = "corda.messaging"
        const val RPC_CONFIG: String = "corda.rpc"
        const val RPC_ADDRESS_CONFIG: String = "address"
        const val RPC_DESCRIPTION_CONFIG: String = "context.description"
        const val RPC_TITLE_CONFIG: String = "context.title"
    }

    private var receivedSnapshot = false

    private var sub: AutoCloseable? = null
    private var bootstrapConfig: Config? = null

    private var server: HttpRpcServer? = null
    private var securityManager: RPCSecurityManager? = null
    private var sslCertReadService: SslCertReadService? = null

    override val isRunning: Boolean
        get() = receivedSnapshot

    fun start(bootstrapConfig: Config) {
        log.info("Starting with bootstrap config")
        this.bootstrapConfig = bootstrapConfig
        this.start()
    }

    override fun start() {
        log.info("Starting from lifecycle event")
        if (bootstrapConfig == null) {
            val message = "Use the other start method available and pass in the bootstrap configuration"
            log.error(message)
            throw CordaRuntimeException(message)
        }

        sub = configurationReadService.registerForUpdates(::onConfigurationUpdated)
        configurationReadService.start()
        configurationReadService.bootstrapConfig(bootstrapConfig!!)
    }

    private fun onConfigurationUpdated(changedKeys: Set<String>, currentConfigurationSnapshot: Map<String, Config>) {
        log.info("Gateway component received lifecycle event, changedKeys: $changedKeys")
        if (MESSAGING_CONFIG in changedKeys) {
            if (receivedSnapshot) {
                log.info("Config update received")
                log.info("Config update contains kafka config")
                lifeCycleCoordinator.postEvent(MessagingConfigUpdateEvent(currentConfigurationSnapshot))
            } else {
                receivedSnapshot = true
                log.info("Config snapshot received")
                lifeCycleCoordinator.postEvent(ConfigReceivedEvent(currentConfigurationSnapshot))
            }
        }
        if (RPC_CONFIG in changedKeys) {
            log.info("Config update received")
            log.info("Config update contains RPC config")

            server?.stop()
            securityManager?.stop()
            val securityManager = rpcSecurityManagerFactory.createRPCSecurityManager().also {
                this.securityManager = it
                it.start()
            }

            sslCertReadService?.stop()
            val keyStoreInfo = sslCertReadServiceFactory.create().let {
                this.sslCertReadService = it
                it.start()
                it.getOrCreateKeyStore()
            }

            val httpRpcSettings = HttpRpcSettings(
                address = NetworkHostAndPort.parse(currentConfigurationSnapshot[RPC_CONFIG]!!.getString(RPC_ADDRESS_CONFIG)),
                context = HttpRpcContext(
                    version = "1",
                    basePath = "/api",
                    description = currentConfigurationSnapshot[RPC_CONFIG]!!.getString(RPC_DESCRIPTION_CONFIG),
                    title = currentConfigurationSnapshot[RPC_CONFIG]!!.getString(RPC_TITLE_CONFIG)
                ),
                ssl = HttpRpcSSLSettings(keyStoreInfo.path, keyStoreInfo.password),
                sso = null,
                maxContentLength = MAX_CONTENT_LENGTH_DEFAULT_VALUE
            )

            server = httpRpcServerFactory.createHttpRpcServer(
                rpcOpsImpls = rpcOps,
                rpcSecurityManager = securityManager,
                httpRpcSettings = httpRpcSettings,
                devMode = true,
                cordappClassLoader = this::class.java.classLoader
            ).also { it.start() }
        }
    }

    override fun stop() {
        sub?.close()
        sub = null
        server?.close()
        server = null
    }
}