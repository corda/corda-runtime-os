package net.corda.permissions.management.internal

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.permissions.management.PermissionManagementRequest
import net.corda.data.permissions.management.PermissionManagementResponse
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.helper.getConfig
import net.corda.libs.permission.PermissionValidator
import net.corda.libs.permissions.manager.BasicAuthenticationService
import net.corda.libs.permissions.manager.PermissionManager
import net.corda.libs.permissions.manager.factory.PermissionManagerFactory
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.permissions.management.cache.PermissionManagementCacheService
import net.corda.permissions.validation.PermissionValidationService
import net.corda.permissions.validation.cache.PermissionValidationCacheService
import net.corda.schema.Schemas.Rest.REST_PERM_MGMT_REQ_TOPIC
import net.corda.schema.configuration.ConfigKeys.BOOT_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.schema.configuration.ConfigKeys.REST_CONFIG
import net.corda.utilities.VisibleForTesting
import org.slf4j.LoggerFactory

@Suppress("LongParameterList")
internal class PermissionManagementServiceEventHandler(
    private val publisherFactory: PublisherFactory,
    private val permissionManagementCacheService: PermissionManagementCacheService,
    private val permissionValidationCacheService: PermissionValidationCacheService,
    private val permissionValidationService: PermissionValidationService,
    private val permissionManagerFactory: PermissionManagerFactory,
    private val configurationReadService: ConfigurationReadService
) : LifecycleEventHandler {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
        const val GROUP_NAME = "rpc.permission.management"
        const val CLIENT_NAME = "rpc.permission.manager"
    }

    @VisibleForTesting
    internal var registrationHandle: RegistrationHandle? = null

    @VisibleForTesting
    internal var rpcSender: RPCSender<PermissionManagementRequest, PermissionManagementResponse>? = null

    @Volatile
    internal var permissionManager: PermissionManager? = null

    internal val permissionValidator: PermissionValidator
        get() = permissionValidationService.permissionValidator

    @Volatile
    internal var basicAuthenticationService: BasicAuthenticationService? = null

    @Volatile
    private var configSubscription: AutoCloseable? = null

    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> {
                registrationHandle?.close()
                registrationHandle = coordinator.followStatusChangesByName(
                    setOf(
                        LifecycleCoordinatorName.forComponent<PermissionManagementCacheService>(),
                        LifecycleCoordinatorName.forComponent<PermissionValidationCacheService>(),
                        LifecycleCoordinatorName.forComponent<PermissionValidationService>(),
                        LifecycleCoordinatorName.forComponent<ConfigurationReadService>()
                    )
                )
                permissionValidationService.start()
                permissionManagementCacheService.start()
            }
            is RegistrationStatusChangeEvent -> {
                log.info("Registration status change received: ${event.status.name}.")
                when (event.status) {
                    LifecycleStatus.UP -> {
                        log.info("Registering for updates from configuration read service.")
                        configSubscription?.close()
                        configSubscription = configurationReadService.registerComponentForUpdates(
                            coordinator,
                            setOf(BOOT_CONFIG, MESSAGING_CONFIG, REST_CONFIG)
                        )
                    }
                    LifecycleStatus.DOWN -> {
                        permissionManager?.stop()
                        permissionManager = null
                        basicAuthenticationService?.stop()
                        basicAuthenticationService = null
                        coordinator.updateStatus(LifecycleStatus.DOWN)
                    }
                    LifecycleStatus.ERROR -> {
                        coordinator.stop()
                        coordinator.updateStatus(LifecycleStatus.ERROR)
                    }
                }
            }
            is ConfigChangedEvent -> {
                log.info("Received new configuration event. Creating and starting RPCSender and permission manager.")
                coordinator.updateStatus(LifecycleStatus.DOWN)
                val messagingConfig = event.config.getConfig(MESSAGING_CONFIG)
                createAndStartRpcSender(messagingConfig)
                val rpcConfig = event.config[REST_CONFIG]!!
                createPermissionManager(rpcConfig)
                coordinator.updateStatus(LifecycleStatus.UP)
            }
            is StopEvent -> {
                permissionValidationService.stop()
                permissionManagementCacheService.stop()
                configSubscription?.close()
                configSubscription = null
                rpcSender?.close()
                rpcSender = null
                permissionManager?.stop()
                permissionManager = null
                registrationHandle?.close()
                registrationHandle = null
                coordinator.updateStatus(LifecycleStatus.DOWN)
            }
        }
    }

    private fun createPermissionManager(config: SmartConfig) {
        val permissionManagementCacheRef = permissionManagementCacheService.permissionManagementCacheRef

        val permissionValidationCacheRef = permissionValidationCacheService.permissionValidationCacheRef

        permissionManager?.stop()
        log.info("Creating and starting permission manager.")
        permissionManager =
            permissionManagerFactory.createPermissionManager(
                config,
                rpcSender!!,
                permissionManagementCacheRef,
                permissionValidationCacheRef
            )
                .also { it.start() }

        basicAuthenticationService?.stop()
        log.info("Creating and starting basic authentication service using permission system.")
        basicAuthenticationService = permissionManagerFactory.createBasicAuthenticationService(permissionManagementCacheRef)
            .also { it.start() }
    }

    private fun createAndStartRpcSender(messagingConfig: SmartConfig) {
        rpcSender?.close()
        rpcSender = publisherFactory.createRPCSender(
            RPCConfig(
                GROUP_NAME,
                CLIENT_NAME,
                REST_PERM_MGMT_REQ_TOPIC,
                PermissionManagementRequest::class.java,
                PermissionManagementResponse::class.java
            ),
            messagingConfig
        ).also { it.start() }
    }
}
