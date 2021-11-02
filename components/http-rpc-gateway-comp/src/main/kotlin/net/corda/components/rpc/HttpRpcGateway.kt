package net.corda.components.rpc

import net.corda.configuration.read.ConfigurationReadService
import net.corda.httprpc.PluggableRPCOps
import net.corda.httprpc.RpcOps
import net.corda.httprpc.security.read.RPCSecurityManager
import net.corda.httprpc.security.read.RPCSecurityManagerFactory
import net.corda.httprpc.server.HttpRpcServer
import net.corda.httprpc.server.config.models.AzureAdSettings
import net.corda.httprpc.server.config.models.HttpRpcContext
import net.corda.httprpc.server.config.models.HttpRpcSSLSettings
import net.corda.httprpc.server.config.models.HttpRpcSettings
import net.corda.httprpc.server.config.models.HttpRpcSettings.Companion.MAX_CONTENT_LENGTH_DEFAULT_VALUE
import net.corda.httprpc.server.config.models.SsoSettings
import net.corda.httprpc.server.factory.HttpRpcServerFactory
import net.corda.httprpc.ssl.SslCertReadService
import net.corda.httprpc.ssl.SslCertReadServiceFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleEvent
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.NetworkHostAndPort
import net.corda.v5.base.util.contextLogger

class ConfigReceivedEvent(val currentConfigurationSnapshot: Map<String, SmartConfig>) : LifecycleEvent
class MessagingConfigUpdateEvent(val currentConfigurationSnapshot: Map<String, SmartConfig>) : LifecycleEvent

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
        private val log = contextLogger()
        const val MESSAGING_CONFIG = "corda.messaging"
        const val RPC_CONFIG = "corda.rpc"
        const val RPC_ADDRESS_CONFIG = "address"
        const val RPC_DESCRIPTION_CONFIG = "context.description"
        const val RPC_TITLE_CONFIG = "context.title"
        const val MAX_CONTENT_LENGTH_CONFIG = "maxContentLength"
        const val AZURE_CLIENT_ID_CONFIG = "sso.azureAd.clientId"
        const val AZURE_TENANT_ID_CONFIG = "sso.azureAd.tenantId"
        const val AZURE_CLIENT_SECRET_CONFIG = "sso.azureAd.clientSecret"
    }

    private var receivedSnapshot = false

    private var sub: AutoCloseable? = null
    private var bootstrapConfig: SmartConfig? = null

    private var server: HttpRpcServer? = null
    private var securityManager: RPCSecurityManager? = null
    private var sslCertReadService: SslCertReadService? = null

    override val isRunning: Boolean
        get() = receivedSnapshot

    fun start(bootstrapConfig: SmartConfig) {
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

    private fun onConfigurationUpdated(changedKeys: Set<String>, currentConfigurationSnapshot: Map<String, SmartConfig>) {
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

            val configSnapshot = currentConfigurationSnapshot[RPC_CONFIG]!!
                val httpRpcSettings = HttpRpcSettings(
                    address = NetworkHostAndPort.parse(configSnapshot.getString(RPC_ADDRESS_CONFIG)),
                context = HttpRpcContext(
                    version = "1",
                    basePath = "/api",
                    description = configSnapshot.getString(RPC_DESCRIPTION_CONFIG),
                    title = configSnapshot.getString(RPC_TITLE_CONFIG)
                ),
                ssl = HttpRpcSSLSettings(keyStoreInfo.path, keyStoreInfo.password),
                sso = configSnapshot.retrieveSsoOptions(),
                maxContentLength = configSnapshot.retrieveMaxContentLength()
            )

            server = httpRpcServerFactory.createHttpRpcServer(
                rpcOpsImpls = rpcOps,
                rpcSecurityManager = securityManager,
                httpRpcSettings = httpRpcSettings,
                devMode = true
            ).also { it.start() }
        }
    }

    private fun SmartConfig.retrieveSsoOptions(): SsoSettings? {
        return if(!hasPath(AZURE_CLIENT_ID_CONFIG) || !hasPath(AZURE_TENANT_ID_CONFIG)) {
            null
        } else {
            val clientId = getString(AZURE_CLIENT_ID_CONFIG)
            val tenantId = getString(AZURE_TENANT_ID_CONFIG)
            val clientSecret = AZURE_CLIENT_SECRET_CONFIG.let {
                if (hasPath(it)) {
                    getString(it)
                } else null
            }
            SsoSettings(AzureAdSettings(clientId, clientSecret, tenantId))
        }
    }

    private fun SmartConfig.retrieveMaxContentLength(): Int {
        return if (hasPath(MAX_CONTENT_LENGTH_CONFIG)) {
            getInt(MAX_CONTENT_LENGTH_CONFIG)
        } else {
            MAX_CONTENT_LENGTH_DEFAULT_VALUE
        }
    }

    override fun stop() {
        sub?.close()
        sub = null
        server?.close()
        server = null
        configurationReadService.stop()
        securityManager?.stop()
        securityManager = null
        sslCertReadService?.stop()
        sslCertReadService = null
    }
}