package net.corda.components.rpc.internal

import net.corda.components.rbac.RBACSecurityManagerService
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.httprpc.PluggableRestResource
import net.corda.httprpc.RestResource
import net.corda.httprpc.server.RestServer
import net.corda.httprpc.server.config.models.AzureAdSettings
import net.corda.httprpc.server.config.models.RestContext
import net.corda.httprpc.server.config.models.RestSSLSettings
import net.corda.httprpc.server.config.models.RestServerSettings
import net.corda.httprpc.server.config.models.RestServerSettings.Companion.MAX_CONTENT_LENGTH_DEFAULT_VALUE
import net.corda.httprpc.server.config.models.SsoSettings
import net.corda.httprpc.server.factory.RestServerFactory
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
import net.corda.schema.configuration.ConfigKeys.REST_ADDRESS
import net.corda.schema.configuration.ConfigKeys.REST_AZUREAD_CLIENT_ID
import net.corda.schema.configuration.ConfigKeys.REST_AZUREAD_CLIENT_SECRET
import net.corda.schema.configuration.ConfigKeys.REST_AZUREAD_TENANT_ID
import net.corda.schema.configuration.ConfigKeys.REST_CONFIG
import net.corda.schema.configuration.ConfigKeys.REST_CONTEXT_DESCRIPTION
import net.corda.schema.configuration.ConfigKeys.REST_CONTEXT_TITLE
import net.corda.schema.configuration.ConfigKeys.REST_MAX_CONTENT_LENGTH
import net.corda.schema.configuration.ConfigKeys.REST_WEBSOCKET_CONNECTION_IDLE_TIMEOUT_MS
import net.corda.utilities.NetworkHostAndPort
import net.corda.utilities.PathProvider
import net.corda.utilities.TempPathProvider
import net.corda.utilities.VisibleForTesting
import net.corda.v5.base.util.contextLogger
import java.util.function.Supplier

@Suppress("LongParameterList")
internal class HttpRpcGatewayEventHandler(
    private val permissionManagementService: PermissionManagementService,
    private val configurationReadService: ConfigurationReadService,
    private val restServerFactory: RestServerFactory,
    private val rbacSecurityManagerService: RBACSecurityManagerService,
    private val sslCertReadServiceFactory: SslCertReadServiceFactory,
    private val dynamicRestResourcesProvider: Supplier<List<PluggableRestResource<out RestResource>>>,
    private val tempPathProvider: PathProvider = TempPathProvider()
) : LifecycleEventHandler {

    private companion object {
        val log = contextLogger()

        const val MULTI_PART_DIR = "multipart"

        const val CONFIG_SUBSCRIPTION = "CONFIG_SUBSCRIPTION"
    }

    @VisibleForTesting
    internal var server: RestServer? = null

    @VisibleForTesting
    internal var sslCertReadService: SslCertReadService? = null

    @VisibleForTesting
    internal var registration: RegistrationHandle? = null

    @Volatile
    @VisibleForTesting
    internal var rpcConfig: SmartConfig? = null

    @Volatile
    @VisibleForTesting
    internal var dependenciesUp = false

    @Suppress("NestedBlockDepth")
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

                log.info("Subscribe to configuration updates.")
                coordinator.createManagedResource(CONFIG_SUBSCRIPTION) {
                    configurationReadService.registerComponentForUpdates(
                        coordinator,
                        setOf(BOOT_CONFIG, REST_CONFIG)
                    )
                }

                val numberOfRpcOps = dynamicRestResourcesProvider.get().filterIsInstance<Lifecycle>()
                    .map {
                        log.info("Starting: ${it.javaClass.simpleName}")
                        it.start()
                    }
                    .count()
                log.info("Started $numberOfRpcOps RPCOps that have lifecycle.")
            }
            is RegistrationStatusChangeEvent -> {
                when (event.status) {
                    LifecycleStatus.UP -> {
                        log.info("Registration received UP status. Registering for configuration updates.")
                        dependenciesUp = true
                        rpcConfig.let {
                            if (it == null) {
                                log.info("Configuration has not been received yet")
                            } else {
                                upTransition(coordinator, it)
                            }
                        }

                    }
                    LifecycleStatus.DOWN -> {
                        log.info("Registration received DOWN status. Stopping the Http RPC Gateway.")
                        downTransition()
                    }
                    LifecycleStatus.ERROR -> {
                        log.info("Registration received ERROR status. Stopping the Http RPC Gateway.")
                        coordinator.postEvent(StopEvent(true))
                    }
                }
            }
            is ConfigChangedEvent -> {
                log.info("Gateway component received configuration update event, changedKeys: ${event.keys}")
                coordinator.updateStatus(LifecycleStatus.DOWN)

                val config = event.config[REST_CONFIG]!!.withFallback(
                    event.config[BOOT_CONFIG]
                )
                rpcConfig = config
                if (dependenciesUp) {
                    upTransition(coordinator, config)
                } else {
                    log.info("Dependencies have not been satisfied yet")
                }
            }
            is StopEvent -> {
                log.info("Stop event received, stopping dependencies.")
                registration?.close()
                registration = null

                permissionManagementService.stop()
                rbacSecurityManagerService.stop()

                dynamicRestResourcesProvider.get().filterIsInstance<Lifecycle>().forEach {
                    log.info("Stopping: ${it.javaClass.simpleName}")
                    it.stop()
                }

                downTransition()
            }
        }
    }

    private fun upTransition(coordinator: LifecycleCoordinator, config: SmartConfig) {
        createAndStartHttpRpcServer(config)
        coordinator.updateStatus(LifecycleStatus.UP)
    }

    private fun downTransition() {
        log.info("Performing down transition.")
        server?.close()
        server = null
        sslCertReadService?.stop()
        sslCertReadService = null
    }

    private fun createAndStartHttpRpcServer(config: SmartConfig) {
        log.info("Stopping any running HTTP RPC Server and endpoints.")
        server?.close()
        sslCertReadService?.stop()

        val keyStoreInfo = sslCertReadServiceFactory.create().let {
            this.sslCertReadService = it
            it.start()
            it.getOrCreateKeyStore()
        }

        val restServerSettings = RestServerSettings(
            address = NetworkHostAndPort.parse(config.getString(REST_ADDRESS)),
            context = RestContext(
                version = "1",
                basePath = "/api",
                description = config.getString(REST_CONTEXT_DESCRIPTION),
                title = config.getString(REST_CONTEXT_TITLE)
            ),
            ssl = RestSSLSettings(keyStoreInfo.path, keyStoreInfo.password),
            sso = config.retrieveSsoOptions(),
            maxContentLength = config.retrieveMaxContentLength(),
            webSocketIdleTimeoutMs = config.getInt(REST_WEBSOCKET_CONNECTION_IDLE_TIMEOUT_MS).toLong()
        )

        val multiPartDir = tempPathProvider.getOrCreate(config, MULTI_PART_DIR)

        log.info("Starting HTTP RPC Server.")
        val rpcOps = dynamicRestResourcesProvider.get()
        server = restServerFactory.createRestServer(
            restResourceImpls = rpcOps,
            restSecurityManagerSupplier = rbacSecurityManagerService::securityManager,
            restServerSettings = restServerSettings,
            multiPartDir = multiPartDir
        ).also { it.start() }
    }

    private fun SmartConfig.retrieveSsoOptions(): SsoSettings? {
        return if (!hasPath(REST_AZUREAD_CLIENT_ID) || !hasPath(REST_AZUREAD_TENANT_ID)) {
            log.info("AzureAD connection is not configured.")
            null
        } else {
            val clientId = getString(REST_AZUREAD_CLIENT_ID)
            val tenantId = getString(REST_AZUREAD_TENANT_ID)
            val clientSecret = REST_AZUREAD_CLIENT_SECRET.let {
                if (hasPath(it)) {
                    getString(it)
                } else null
            }
            SsoSettings(AzureAdSettings(clientId, clientSecret, tenantId))
        }
    }

    private fun SmartConfig.retrieveMaxContentLength(): Int {
        return if (hasPath(REST_MAX_CONTENT_LENGTH)) {
            getInt(REST_MAX_CONTENT_LENGTH)
        } else {
            MAX_CONTENT_LENGTH_DEFAULT_VALUE
        }
    }
}
