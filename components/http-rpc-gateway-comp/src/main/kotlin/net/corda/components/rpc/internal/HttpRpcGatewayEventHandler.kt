package net.corda.components.rpc.internal

import net.corda.components.rbac.RBACSecurityManagerService
import net.corda.configuration.read.ConfigurationReadService
import net.corda.httprpc.PluggableRPCOps
import net.corda.httprpc.RpcOps
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
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.permissions.management.PermissionManagementService
import net.corda.schema.configuration.ConfigKeys.BOOT_CONFIG
import net.corda.schema.configuration.ConfigKeys.RPC_ADDRESS
import net.corda.schema.configuration.ConfigKeys.RPC_AZUREAD_CLIENT_ID
import net.corda.schema.configuration.ConfigKeys.RPC_AZUREAD_CLIENT_SECRET
import net.corda.schema.configuration.ConfigKeys.RPC_AZUREAD_TENANT_ID
import net.corda.schema.configuration.ConfigKeys.RPC_CONFIG
import net.corda.schema.configuration.ConfigKeys.RPC_CONTEXT_DESCRIPTION
import net.corda.schema.configuration.ConfigKeys.RPC_CONTEXT_TITLE
import net.corda.schema.configuration.ConfigKeys.RPC_MAX_CONTENT_LENGTH
import net.corda.utilities.PathProvider
import net.corda.utilities.TempPathProvider
import net.corda.v5.base.annotations.VisibleForTesting
import net.corda.v5.base.util.NetworkHostAndPort
import net.corda.v5.base.util.contextLogger
import java.util.function.Supplier

@Suppress("LongParameterList")
internal class HttpRpcGatewayEventHandler(
    private val permissionManagementService: PermissionManagementService,
    private val configurationReadService: ConfigurationReadService,
    private val httpRpcServerFactory: HttpRpcServerFactory,
    private val rbacSecurityManagerService: RBACSecurityManagerService,
    private val sslCertReadServiceFactory: SslCertReadServiceFactory,
    private val dynamicRpcOpsProvider: Supplier<List<PluggableRPCOps<out RpcOps>>>,
    private val tempPathProvider: PathProvider = TempPathProvider()
) : LifecycleEventHandler {

    private companion object {
        val log = contextLogger()

        const val MULTI_PART_DIR = "multipart"
    }

    @VisibleForTesting
    internal var server: HttpRpcServer? = null

    @VisibleForTesting
    internal var sslCertReadService: SslCertReadService? = null

    @VisibleForTesting
    internal var registration: RegistrationHandle? = null

    @VisibleForTesting
    internal var sub: AutoCloseable? = null

    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> {
                log.info("Received start event, following dependencies for status updates.")

                registration?.close()
                registration = coordinator.followStatusChangesByName(
                    setOf(
                        LifecycleCoordinatorName.forComponent<PermissionManagementService>(),
                        LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
                        LifecycleCoordinatorName.forComponent<RBACSecurityManagerService>()
                    )
                )

                log.info("Starting permission service and RBAC security manager.")
                permissionManagementService.start()
                rbacSecurityManagerService.start()
            }
            is RegistrationStatusChangeEvent -> {
                when (event.status) {
                    LifecycleStatus.UP -> {
                        log.info("Registration received UP status. Registering for configuration updates.")
                        // Http RPC Server can only be created when security manager and permission service are ready.
                        sub = configurationReadService.registerForUpdates(::onConfigurationUpdated)
                        coordinator.updateStatus(LifecycleStatus.UP)
                    }
                    LifecycleStatus.DOWN -> {
                        log.info("Registration received DOWN status. Stopping the Http RPC Gateway.")
                        coordinator.postEvent(StopEvent())
                    }
                    LifecycleStatus.ERROR -> {
                        log.info("Registration received ERROR status. Stopping the Http RPC Gateway.")
                        coordinator.postEvent(StopEvent(true))
                    }
                }
            }
            is StopEvent -> {
                log.info("Stop event received, stopping dependencies.")
                registration?.close()
                registration = null
                sub?.close()
                sub = null
                permissionManagementService.stop()
                rbacSecurityManagerService.stop()
                server?.close()
                server = null
                sslCertReadService?.stop()
                sslCertReadService = null
                dynamicRpcOpsProvider.get().filterIsInstance<Lifecycle>().forEach { it.stop() }
            }
        }
    }

    private fun onConfigurationUpdated(changedKeys: Set<String>, currentConfigurationSnapshot: Map<String, SmartConfig>) {
        log.info("Gateway component received configuration update event, changedKeys: $changedKeys")

        if (RPC_CONFIG in changedKeys) {
            log.info("RPC config received. Recreating HTTP RPC Server.")

            val config = currentConfigurationSnapshot[RPC_CONFIG]!!.withFallback(
                currentConfigurationSnapshot[BOOT_CONFIG]
            )

            createAndStartHttpRpcServer(config)
        }
    }

    private fun createAndStartHttpRpcServer(config: SmartConfig) {
        log.info("Stopping any running HTTP RPC Server and endpoints.")
        server?.stop()
        sslCertReadService?.stop()

        val keyStoreInfo = sslCertReadServiceFactory.create().let {
            this.sslCertReadService = it
            it.start()
            it.getOrCreateKeyStore()
        }

        val httpRpcSettings = HttpRpcSettings(
            address = NetworkHostAndPort.parse(config.getString(RPC_ADDRESS)),
            context = HttpRpcContext(
                version = "1",
                basePath = "/api",
                description = config.getString(RPC_CONTEXT_DESCRIPTION),
                title = config.getString(RPC_CONTEXT_TITLE)
            ),
            ssl = HttpRpcSSLSettings(keyStoreInfo.path, keyStoreInfo.password),
            sso = config.retrieveSsoOptions(),
            maxContentLength = config.retrieveMaxContentLength()
        )

        val multiPartDir = tempPathProvider.getOrCreate(config, MULTI_PART_DIR)

        log.info("Starting HTTP RPC Server.")
        val rpcOps = dynamicRpcOpsProvider.get()
        server = httpRpcServerFactory.createHttpRpcServer(
            rpcOpsImpls = rpcOps,
            rpcSecurityManager = rbacSecurityManagerService.securityManager,
            httpRpcSettings = httpRpcSettings,
            multiPartDir = multiPartDir
        ).also { it.start() }

        val numberOfRpcOps = rpcOps.filterIsInstance<Lifecycle>()
            .map { it.start() }
            .count()
        log.info("Started $numberOfRpcOps RPCOps that have lifecycle.")
    }

    private fun SmartConfig.retrieveSsoOptions(): SsoSettings? {
        return if (!hasPath(RPC_AZUREAD_CLIENT_ID) || !hasPath(RPC_AZUREAD_TENANT_ID)) {
            log.info("AzureAD connection is not configured.")
            null
        } else {
            val clientId = getString(RPC_AZUREAD_CLIENT_ID)
            val tenantId = getString(RPC_AZUREAD_TENANT_ID)
            val clientSecret = RPC_AZUREAD_CLIENT_SECRET.let {
                if (hasPath(it)) {
                    getString(it)
                } else null
            }
            SsoSettings(AzureAdSettings(clientId, clientSecret, tenantId))
        }
    }

    private fun SmartConfig.retrieveMaxContentLength(): Int {
        return if (hasPath(RPC_MAX_CONTENT_LENGTH)) {
            getInt(RPC_MAX_CONTENT_LENGTH)
        } else {
            MAX_CONTENT_LENGTH_DEFAULT_VALUE
        }
    }
}