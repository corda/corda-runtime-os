package net.corda.permissions.service.internal

import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus.DOWN
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.permissions.cache.PermissionCacheService
import net.corda.permissions.management.PermissionManagementService
import net.corda.rpc.permissions.PermissionValidationService
import net.corda.v5.base.annotations.VisibleForTesting
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug

internal class PermissionServiceComponentEventHandler(
    private val permissionManagementService: PermissionManagementService,
    private val permissionValidationService: PermissionValidationService,
    private val permissionCacheService: PermissionCacheService
) : LifecycleEventHandler {

    private companion object {
        val logger = contextLogger()
    }

    @VisibleForTesting
    internal var registration: RegistrationHandle? = null

    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> {
                followServicesForStatusUpdates(coordinator)

                logger.debug { "Starting the Permission Cache Service" }
                permissionCacheService.start()
                logger.debug { "Starting the Permission Management Service" }
                permissionManagementService.start()
                logger.debug { "Starting the Permission Validation Service" }
                permissionValidationService.start()
            }
            is RegistrationStatusChangeEvent -> {
                logger.debug { "Permission Service Component received lifecycle status ${event.status.name}." }
                coordinator.updateStatus(event.status)
            }
            is StopEvent -> {
                registration?.close()
                registration = null
                permissionManagementService.stop()
                permissionValidationService.stop()
                permissionCacheService.stop()
                coordinator.updateStatus(DOWN)
            }
        }
    }

    private fun followServicesForStatusUpdates(coordinator: LifecycleCoordinator) {
        registration?.close()

        registration = coordinator.followStatusChangesByName(
            setOf(
                LifecycleCoordinatorName.forComponent<PermissionCacheService>(),
                LifecycleCoordinatorName.forComponent<PermissionValidationService>(),
                LifecycleCoordinatorName.forComponent<PermissionManagementService>()
            )
        )
    }
}