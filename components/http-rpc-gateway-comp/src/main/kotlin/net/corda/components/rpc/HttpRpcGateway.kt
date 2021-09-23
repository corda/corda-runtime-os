package net.corda.components.rpc

import com.typesafe.config.Config
import net.corda.configuration.read.ConfigurationReadService
import net.corda.httprpc.security.read.RPCSecurityManager
import net.corda.httprpc.security.read.RPCSecurityManagerFactory
import net.corda.httprpc.server.HttpRpcServer
import net.corda.httprpc.server.config.models.HttpRpcContext
import net.corda.httprpc.server.config.models.HttpRpcSettings
import net.corda.httprpc.server.config.models.HttpRpcSettings.Companion.MAX_CONTENT_LENGTH_DEFAULT_VALUE
import net.corda.httprpc.server.factory.HttpRpcServerFactory

import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleEvent
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.NetworkHostAndPort
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger

class ConfigReceivedEvent(val currentConfigurationSnapshot: Map<String, Config>) : LifecycleEvent
class MessagingConfigUpdateEvent(val currentConfigurationSnapshot: Map<String, Config>) : LifecycleEvent

// Should this become a component with a factory so that all its dependencies, other than the
// LifecycleCoordinator and injected. The LifecycleCoordinator can then be passed into the
// create method
class HttpRpcGateway(
    private val lifeCycleCoordinator: LifecycleCoordinator,
    private val configurationReadService: ConfigurationReadService,
    private val httpRpcServerFactory: HttpRpcServerFactory,
    private val rpcSecurityManagerFactory: RPCSecurityManagerFactory
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

        val listener = { changedKeys: Set<String>, currentConfigurationSnapshot: Map<String, Config> ->
            log.info("Gateway component received lifecycle event")
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

                val httpRpcSettings = HttpRpcSettings(
                    address = NetworkHostAndPort.parse(currentConfigurationSnapshot[RPC_CONFIG]!!.getString(RPC_ADDRESS_CONFIG)),
                    context = HttpRpcContext(
                        version = "1",
                        basePath = "/api",
                        description = currentConfigurationSnapshot[RPC_CONFIG]!!.getString(RPC_DESCRIPTION_CONFIG),
                        title = currentConfigurationSnapshot[RPC_CONFIG]!!.getString(RPC_TITLE_CONFIG)
                    ),
                    ssl = null,
                    sso = null,
                    maxContentLength = MAX_CONTENT_LENGTH_DEFAULT_VALUE
                )

                server?.stop()
                securityManager?.stop()
                val securityManager = rpcSecurityManagerFactory.createRPCSecurityManager().also {
                    this.securityManager = it
                }
                securityManager.start()
                server = httpRpcServerFactory.createHttpRpcServer(
                    rpcOpsImpls = emptyList(),
                    rpcSecurityManager = securityManager,
                    httpRpcSettings = httpRpcSettings,
                    devMode = false,
                    cordappClassLoader = this::class.java.classLoader
                )
                server?.start()

            }
        }
        sub = configurationReadService.registerForUpdates(listener)

        configurationReadService.start()
    }

    override fun stop() {
        sub?.close()
        sub = null
        server?.close()
        server = null
    }
}