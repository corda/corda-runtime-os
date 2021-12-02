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
import net.corda.messaging.api.subscription.factory.config.RPCConfig
import net.corda.permissions.cache.PermissionCacheService
import net.corda.rpc.schema.Schema
import net.corda.v5.base.annotations.VisibleForTesting

private class NewConfigurationReceivedEvent(val config: SmartConfig) : LifecycleEvent

internal class PermissionManagementServiceEventHandler(
    private val publisherFactory: PublisherFactory,
    private val permissionCacheService: PermissionCacheService,
    private val permissionManagerFactory: PermissionManagerFactory,
    private val configurationReadService: ConfigurationReadService
) : LifecycleEventHandler {

    private companion object {
        const val GROUP_NAME = "rpc.permission.management"
        const val CLIENT_NAME = "rpc.permission.manager"
        const val KAFKA_COMMON_BOOTSTRAP_SERVER = "messaging.kafka.common.bootstrap.servers"
    }

    @VisibleForTesting
    internal var registrationHandle: RegistrationHandle? = null

    @VisibleForTesting
    internal var rpcSender: RPCSender<PermissionManagementRequest, PermissionManagementResponse>? = null

    internal var permissionManager: PermissionManager? = null

    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> {
                handleStartEvent(coordinator)
            }
            is NewConfigurationReceivedEvent -> {
                handleNewConfigurationReceived(event, coordinator)
            }
            is RegistrationStatusChangeEvent -> {
                // These status updates are from PermissionCacheService
                when (event.status) {
                    LifecycleStatus.UP -> {
                        handlePermissionCacheUp(coordinator)
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

    private fun handleStartEvent(coordinator: LifecycleCoordinator) {
        registrationHandle?.close()
        registrationHandle = coordinator.followStatusChangesByName(
            setOf(
                LifecycleCoordinatorName.forComponent<PermissionCacheService>()
            )
        )
        configurationReadService.registerForUpdates { changedKeys: Set<String>, config: Map<String, SmartConfig> ->
            if (KAFKA_COMMON_BOOTSTRAP_SERVER in changedKeys) {
                val newConfig = config[KAFKA_COMMON_BOOTSTRAP_SERVER]
                coordinator.postEvent(NewConfigurationReceivedEvent(newConfig!!))
            }
        }
    }

    private fun handlePermissionCacheUp(coordinator: LifecycleCoordinator) {
        val permissionCache = permissionCacheService.permissionCache
        checkNotNull(permissionCache) {
            "The PermissionCacheService reported status UP but its permissionCache field was null."
        }
        if (rpcSender != null) {
            permissionManager?.stop()
            permissionManager = permissionManagerFactory.create(rpcSender!!, permissionCache)
                .also { it.start() }
            coordinator.updateStatus(LifecycleStatus.UP)
        }
    }

    private fun handleNewConfigurationReceived(
        event: NewConfigurationReceivedEvent,
        coordinator: LifecycleCoordinator
    ) {
        createAndStartRpcSender(event.config)

        // if permission cache is not up yet, permission manager can be created during its registration UP event instead.
        val permissionCache = permissionCacheService.permissionCache
        if (permissionCache != null) {
            permissionManager?.stop()
            permissionManager = permissionManagerFactory.create(rpcSender!!, permissionCache)
                .also { it.start() }
            coordinator.updateStatus(LifecycleStatus.UP)
        }
    }

    private fun createAndStartRpcSender(kafkaConfig: SmartConfig) {
        rpcSender?.stop()
        rpcSender = publisherFactory.createRPCSender(
            RPCConfig(
                GROUP_NAME,
                CLIENT_NAME,
                Schema.RPC_PERM_MGMT_REQ_TOPIC,
                PermissionManagementRequest::class.java,
                PermissionManagementResponse::class.java
            ),
            kafkaConfig
        ).also { it.start() }
    }
}