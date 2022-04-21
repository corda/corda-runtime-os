package net.corda.permissions.management.internal

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.permissions.management.PermissionManagementRequest
import net.corda.data.permissions.management.PermissionManagementResponse
import net.corda.libs.configuration.SmartConfig
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
import net.corda.messaging.api.config.toMessagingConfig
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.permissions.management.cache.PermissionManagementCacheService
import net.corda.permissions.validation.PermissionValidationService
import net.corda.permissions.validation.cache.PermissionValidationCacheService
import net.corda.schema.Schemas.RPC.Companion.RPC_PERM_MGMT_REQ_TOPIC
import net.corda.schema.configuration.ConfigKeys.BOOT_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.schema.configuration.ConfigKeys.RPC_CONFIG
import net.corda.v5.base.annotations.VisibleForTesting
import net.corda.v5.base.util.contextLogger

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
        val log = contextLogger()
        const val GROUP_NAME = "rpc.permission.management"
        const val CLIENT_NAME = "rpc.permission.manager"
    }

    @VisibleForTesting
    internal var registrationHandle: RegistrationHandle? = null

    @VisibleForTesting
    internal var rpcSender: RPCSender<PermissionManagementRequest, PermissionManagementResponse>? = null

    internal var permissionManager: PermissionManager? = null
    internal var permissionValidator: PermissionValidator? = null
    internal var basicAuthenticationService: BasicAuthenticationService? = null

    private var configSubscription: AutoCloseable? = null

    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> {
                log.info("Received start event, following dependencies.")
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
                            setOf(BOOT_CONFIG, MESSAGING_CONFIG, RPC_CONFIG)
                        )
                        permissionValidator = permissionValidationService.permissionValidator
                    }
                    LifecycleStatus.DOWN -> {
                        permissionManager?.close()
                        permissionManager = null
                        basicAuthenticationService?.close()
                        basicAuthenticationService = null
                        permissionValidator = null
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
                val messagingConfig = event.config.toMessagingConfig()
                createAndStartRpcSender(messagingConfig)
                val rpcConfig = event.config[RPC_CONFIG]!!
                createPermissionManager(rpcConfig)
                coordinator.updateStatus(LifecycleStatus.UP)
            }
            is StopEvent -> {
                log.info("Stop event received, stopping dependencies.")
                permissionValidationService.stop()
                permissionManagementCacheService.stop()
                configSubscription?.close()
                configSubscription = null
                rpcSender?.close()
                rpcSender = null
                permissionManager?.close()
                permissionManager = null
                registrationHandle?.close()
                registrationHandle = null
                permissionValidator = null
                coordinator.updateStatus(LifecycleStatus.DOWN)
            }
        }
    }

    private fun createPermissionManager(config: SmartConfig) {
        val permissionManagementCache = permissionManagementCacheService.permissionManagementCache
        checkNotNull(permissionManagementCache) {
            "Configuration received for permission manager but permission management cache was null."
        }
        val permissionValidationCache = permissionValidationCacheService.permissionValidationCache
        checkNotNull(permissionValidationCache) {
            "Configuration received for permission manager but permission validation cache was null."
        }

        permissionManager?.close()
        log.info("Creating and starting permission manager.")
        permissionManager =
            permissionManagerFactory.createPermissionManager(config, rpcSender!!, permissionManagementCache, permissionValidationCache)
                .also { it.start() }

        basicAuthenticationService?.close()
        log.info("Creating and starting basic authentication service using permission system.")
        basicAuthenticationService = permissionManagerFactory.createBasicAuthenticationService(permissionManagementCache)
            .also { it.start() }
    }

    private fun createAndStartRpcSender(kafkaConfig: SmartConfig) {
        rpcSender?.close()
        rpcSender = publisherFactory.createRPCSender(
            RPCConfig(
                GROUP_NAME,
                CLIENT_NAME,
                RPC_PERM_MGMT_REQ_TOPIC,
                PermissionManagementRequest::class.java,
                PermissionManagementResponse::class.java
            ),
            kafkaConfig
        ).also { it.start() }
    }
}
