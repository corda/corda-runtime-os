package net.corda.components.rpc

import net.corda.components.rpc.internal.HttpRpcGatewayEventHandler
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
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.createCoordinator
import net.corda.permissions.service.PermissionServiceComponent
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.NetworkHostAndPort
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ReferenceCardinality

@Suppress("LongParameterList")
@Component(service = [HttpRpcGateway::class], immediate = true)
class HttpRpcGateway @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = HttpRpcServerFactory::class)
    private val httpRpcServerFactory: HttpRpcServerFactory,
    @Reference(service = RPCSecurityManagerFactory::class)
    private val rpcSecurityManagerFactory: RPCSecurityManagerFactory,
    @Reference(service = SslCertReadServiceFactory::class)
    private val sslCertReadServiceFactory: SslCertReadServiceFactory,
    @Reference(service = PermissionServiceComponent::class)
    private val permissionServiceComponent: PermissionServiceComponent,
    @Reference(service = PluggableRPCOps::class, cardinality = ReferenceCardinality.MULTIPLE)
    private val rpcOps: List<PluggableRPCOps<out RpcOps>>,
) : Lifecycle {

    private companion object {
        val log = contextLogger()
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

    private var coordinator: LifecycleCoordinator = coordinatorFactory.createCoordinator<HttpRpcGateway>(
        HttpRpcGatewayEventHandler(permissionServiceComponent, rpcOps.filterIsInstance<Lifecycle>())
    )

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
        if (bootstrapConfig == null) {
            val message = "Use the other start method available and pass in the bootstrap configuration"
            log.error(message)
            throw CordaRuntimeException(message)
        }

        log.info("Starting lifecycle coordinator for RBAC permission system.")
        coordinator.start()

        log.info("Starting configuration read service.")
        sub = configurationReadService.registerForUpdates(::onConfigurationUpdated)
        configurationReadService.start()
        configurationReadService.bootstrapConfig(bootstrapConfig!!)
    }

    private fun onConfigurationUpdated(changedKeys: Set<String>, currentConfigurationSnapshot: Map<String, SmartConfig>) {
        log.info("Gateway component received configuration update event, changedKeys: $changedKeys")
        if (MESSAGING_CONFIG in changedKeys) {
            if (receivedSnapshot) {
                handleMessagingConfigUpdate()
            } else {
                handleInitialMessagingConfigSnapshot()
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

    private fun handleInitialMessagingConfigSnapshot() {
        receivedSnapshot = true
        log.info("Config snapshot received")
    }

    private fun handleMessagingConfigUpdate() {
        log.info("Config update received")
        log.info("Config update contains kafka config")
    }

    private fun SmartConfig.retrieveSsoOptions(): SsoSettings? {
        return if (!hasPath(AZURE_CLIENT_ID_CONFIG) || !hasPath(AZURE_TENANT_ID_CONFIG)) {
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
        configurationReadService.stop()
        securityManager?.stop()
        securityManager = null
        sslCertReadService?.stop()
        sslCertReadService = null
        coordinator.stop()
    }
}