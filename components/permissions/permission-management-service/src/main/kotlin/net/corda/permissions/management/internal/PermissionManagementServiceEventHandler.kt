package net.corda.permissions.management.internal

import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.permissions.management.PermissionManagementRequest
import net.corda.data.permissions.management.PermissionManagementResponse
import net.corda.libs.configuration.SmartConfig
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
import net.corda.permissions.cache.PermissionCacheService
import net.corda.schema.Schemas.RPC.Companion.RPC_PERM_MGMT_REQ_TOPIC
import net.corda.v5.base.annotations.VisibleForTesting
import net.corda.v5.base.util.contextLogger

private class NewConfigurationReceivedEvent(val config: SmartConfig) : LifecycleEvent

internal class PermissionManagementServiceEventHandler(
    private val publisherFactory: PublisherFactory,
    private val permissionCacheService: PermissionCacheService,
    private val permissionManagerFactory: PermissionManagerFactory,
    private val configurationReadService: ConfigurationReadService
) : LifecycleEventHandler {

    private companion object {
        val log = contextLogger()
        const val GROUP_NAME = "rpc.permission.management"
        const val CLIENT_NAME = "rpc.permission.manager"
        const val RPC_CONFIG = "corda.rpc"
        const val BOOTSTRAP_CONFIG = "corda.boot"
    }

    @VisibleForTesting
    internal var registrationHandle: RegistrationHandle? = null

    @VisibleForTesting
    internal var rpcSender: RPCSender<PermissionManagementRequest, PermissionManagementResponse>? = null

    internal var permissionManager: PermissionManager? = null
    private var configSubscription: AutoCloseable? = null

    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> {
                log.info("Received start event, following ConfigReadService and PermissionCacheService for status updates.")
                registrationHandle?.close()
                registrationHandle = coordinator.followStatusChangesByName(
                    setOf(
                        LifecycleCoordinatorName.forComponent<PermissionCacheService>(),
                        LifecycleCoordinatorName.forComponent<ConfigurationReadService>()
                    )
                )
            }
            is NewConfigurationReceivedEvent -> {
                log.info("Received new configuration event. Creating and starting RPCSender and permission manager.")
                createAndStartRpcSender(event.config)
                createPermissionManager(event.config)
                coordinator.updateStatus(LifecycleStatus.UP)
            }
            is RegistrationStatusChangeEvent -> {
                log.info("Registration status change received: ${event.status.name}.")
                when (event.status) {
                    LifecycleStatus.UP -> {
                        log.info("Registering for updates from configuration read service.")
                        registerForConfigurationUpdates(coordinator)
                    }
                    LifecycleStatus.DOWN -> {
                        permissionManager?.stop()
                        permissionManager = null
                        coordinator.updateStatus(LifecycleStatus.DOWN)
                    }
                    LifecycleStatus.ERROR -> {
                        coordinator.stop()
                        coordinator.updateStatus(LifecycleStatus.ERROR)
                    }
                }
            }
            is StopEvent -> {
                log.info("Stop event received, stopping dependencies and setting status to DOWN.")
                configSubscription?.close()
                configSubscription = null
                rpcSender?.stop()
                rpcSender = null
                permissionManager?.stop()
                permissionManager = null
                registrationHandle?.close()
                registrationHandle = null
                coordinator.updateStatus(LifecycleStatus.DOWN)
            }
        }
    }

    private fun registerForConfigurationUpdates(coordinator: LifecycleCoordinator) {
        configSubscription?.close()
        configSubscription = configurationReadService.registerForUpdates { changedKeys: Set<String>, config: Map<String, SmartConfig> ->
            log.info("Received configuration update event, changedKeys: $changedKeys")
            if (BOOTSTRAP_CONFIG in changedKeys && config.keys.contains(BOOTSTRAP_CONFIG)) {
                val rpcConfig: SmartConfig? = config[RPC_CONFIG]
                val bootConfig = config[BOOTSTRAP_CONFIG]!!
                val newConfig = rpcConfig?.withFallback(bootConfig) ?: bootConfig
                coordinator.postEvent(NewConfigurationReceivedEvent(newConfig))
            } else if (RPC_CONFIG in changedKeys && config.keys.contains(RPC_CONFIG)) {
                coordinator.postEvent(NewConfigurationReceivedEvent(config[RPC_CONFIG]!!))
            }
        }
    }

    private fun createPermissionManager(config: SmartConfig) {
        val permissionCache = permissionCacheService.permissionCache
        checkNotNull(permissionCache) {
            "Configuration received for permission manager but permission cache was null."
        }
        permissionManager?.stop()
        log.info("Creating and starting permission manager.")
        permissionManager = permissionManagerFactory.create(config, rpcSender!!, permissionCache)
            .also { it.start() }
    }

    private fun createAndStartRpcSender(kafkaConfig: SmartConfig) {
        rpcSender?.stop()
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
