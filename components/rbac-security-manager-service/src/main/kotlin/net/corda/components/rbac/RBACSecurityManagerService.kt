package net.corda.components.rbac

import net.corda.httprpc.security.read.RPCSecurityManager
import net.corda.httprpc.security.read.rbac.RBACSecurityManager
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.permissions.management.PermissionManagementService
import net.corda.permissions.validation.PermissionValidationService
import net.corda.v5.base.annotations.VisibleForTesting
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [RBACSecurityManagerService::class])
class RBACSecurityManagerService @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = PermissionManagementService::class)
    private val permissionManagementService: PermissionManagementService,
    @Reference(service = PermissionValidationService::class)
    private val permissionValidationService: PermissionValidationService
) : Lifecycle {

    private companion object {
        val log = contextLogger()
    }

    val securityManager: RPCSecurityManager
        get() {
            validateRpcSecurityManagerRunning()
            return _securityManager!!
        }

    /**
     * This prevents user authorization before the security manager is ready.
     */
    private fun validateRpcSecurityManagerRunning() {
        require(isRunning) {
            "Security Manager is not running."
        }
        requireNotNull(_securityManager) {
            "Security Manager has not been initialized."
        }
    }

    private var _securityManager: RPCSecurityManager? = null

    @VisibleForTesting
    internal var coordinator: LifecycleCoordinator = coordinatorFactory.createCoordinator<RBACSecurityManagerService>(::processEvent)

    @VisibleForTesting
    internal var registration: RegistrationHandle? = null

    @VisibleForTesting
    internal fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> {
                log.info("Received start event, following PermissionServiceComponent for status updates.")
                registration?.close()
                registration = coordinator.followStatusChangesByName(
                    setOf(
                        LifecycleCoordinatorName.forComponent<PermissionValidationService>(),
                        LifecycleCoordinatorName.forComponent<PermissionManagementService>(),
                    )
                )
            }
            is RegistrationStatusChangeEvent -> {
                log.info("Received registration status update for PermissionServiceComponent: ${event.status}.")
                when (event.status) {
                    LifecycleStatus.UP -> {
                        _securityManager = RBACSecurityManager(
                            permissionValidationService.permissionValidator,
                            permissionManagementService.basicAuthenticationService
                        )
                        coordinator.updateStatus(LifecycleStatus.UP)
                    }
                    LifecycleStatus.DOWN -> {
                        coordinator.postEvent(StopEvent())
                    }
                    LifecycleStatus.ERROR -> {
                        coordinator.postEvent(StopEvent(true))
                    }
                }
            }
            is StopEvent -> {
                log.info("Stop event received, stopping dependencies and setting status to DOWN.")
                registration?.close()
                registration = null
                _securityManager = null
            }
        }
    }

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        log.info("Starting lifecycle coordinator")
        coordinator.start()
    }

    override fun stop() {
        coordinator.stop()
    }
}