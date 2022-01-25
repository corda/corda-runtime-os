package net.corda.permissions.service

import net.corda.libs.permission.PermissionValidator
import net.corda.libs.permissions.manager.PermissionManager
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.createCoordinator
import net.corda.permissions.cache.PermissionCacheService
import net.corda.permissions.management.PermissionManagementService
import net.corda.permissions.service.internal.PermissionServiceComponentEventHandler
import net.corda.rpc.permissions.PermissionValidationService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [PermissionServiceComponent::class])
class PermissionServiceComponent @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = PermissionManagementService::class)
    private val permissionManagementService: PermissionManagementService,
    @Reference(service = PermissionValidationService::class)
    private val permissionValidationService: PermissionValidationService,
    @Reference(service = PermissionCacheService::class)
    private val permissionCacheService: PermissionCacheService,
) : Lifecycle {

    private val coordinator = coordinatorFactory.createCoordinator<PermissionServiceComponent>(
        PermissionServiceComponentEventHandler(
            permissionManagementService,
            permissionValidationService,
            permissionCacheService
        )
    )

    /**
     * Expose the Permission Manager to components outside the permission service. Checking the permission service is running ensures the
     * permission manager will not be null.
     */
    val permissionManager: PermissionManager
        get() {
            validatePermissionServiceRunning()
            return permissionManagementService.permissionManager
        }

    /**
     * Expose the Permission Validator to components outside the permission service. Checking the permission service is running ensures the
     * permission validator will not be null.
     */
    val permissionValidator: PermissionValidator
        get() {
            validatePermissionServiceRunning()
            return permissionValidationService.permissionValidator
        }

    private fun validatePermissionServiceRunning() {
        val status = coordinator.status
        check(status == LifecycleStatus.UP) {
            "Permission Service Component is not running. (Status: '$status')."
        }
    }

    override val isRunning: Boolean
        get() = coordinator.isRunning &&
                coordinator.status == LifecycleStatus.UP &&
                permissionManagementService.isRunning

    override fun start() {
        coordinator.start()
    }

    override fun stop() {
        coordinator.close()
    }
}