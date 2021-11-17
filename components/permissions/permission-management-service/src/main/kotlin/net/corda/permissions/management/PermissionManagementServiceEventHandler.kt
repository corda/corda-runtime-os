package net.corda.permissions.management

import net.corda.data.permissions.management.PermissionManagementRequest
import net.corda.data.permissions.management.PermissionManagementResponse
import net.corda.libs.permissions.manager.PermissionManager
import net.corda.libs.permissions.manager.factory.PermissionManagerFactory
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus.DOWN
import net.corda.lifecycle.LifecycleStatus.ERROR
import net.corda.lifecycle.LifecycleStatus.UP
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

class PermissionManagementServiceEventHandler(
    private val publisherFactory: PublisherFactory,
    private val permissionCacheService: PermissionCacheService,
    private val permissionManagerFactory: PermissionManagerFactory
) : LifecycleEventHandler {

    private companion object {
        const val GROUP_NAME = "rpc.permission.management"
        const val CLIENT_NAME = "rpc.permission.manager"
    }

    @VisibleForTesting
    internal var registrationHandle: RegistrationHandle? = null

    @VisibleForTesting
    internal var rpcSender: RPCSender<PermissionManagementRequest, PermissionManagementResponse>? = null

    internal var permissionManager: PermissionManager? = null

    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> {
                registrationHandle?.close()
                registrationHandle = coordinator.followStatusChangesByName(
                    setOf(
                        LifecycleCoordinatorName.forComponent<PermissionCacheService>()
                    )
                )
                rpcSender = publisherFactory.createRPCSender(
                    RPCConfig(
                        GROUP_NAME,
                        CLIENT_NAME,
                        Schema.RPC_PERM_MGMT_REQ_TOPIC,
                        PermissionManagementRequest::class.java,
                        PermissionManagementResponse::class.java
                    )
                ).also { it.start() }
            }
            is RegistrationStatusChangeEvent -> {
                // These status updates are from PermissionCacheService
                when (event.status) {
                    UP -> {
                        val permissionCache = permissionCacheService.permissionCache
                        checkNotNull(permissionCache) {
                            "The ${PermissionCacheService::class.java} should be up and ready to provide the cache"
                        }
                        checkNotNull(rpcSender) { "The ${RPCSender::class.java} must be initialized" }
                        permissionManager = permissionManagerFactory.create(rpcSender!!, permissionCache)
                            .also { it.start() }
                        coordinator.updateStatus(UP)
                    }
                    DOWN -> {
                        permissionManager?.stop()
                        permissionManager = null
                        coordinator.updateStatus(DOWN)
                    }
                    ERROR -> {
                        coordinator.stop()
                        coordinator.updateStatus(ERROR)
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
                coordinator.updateStatus(DOWN)
            }
        }
    }
}