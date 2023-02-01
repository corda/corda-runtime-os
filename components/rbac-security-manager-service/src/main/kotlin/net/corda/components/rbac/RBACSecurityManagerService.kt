package net.corda.components.rbac

import net.corda.httprpc.security.read.RestSecurityManager
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
import net.corda.utilities.VisibleForTesting
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory

@Component(service = [RBACSecurityManagerService::class])
class RBACSecurityManagerService @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = PermissionManagementService::class)
    private val permissionManagementService: PermissionManagementService
) : Lifecycle {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    val securityManager: RestSecurityManager
        get() {
            validateRpcSecurityManagerRunning()
            return innerSecurityManager!!
        }

    /**
     * This prevents user authorization before the security manager is ready.
     */
    private fun validateRpcSecurityManagerRunning() {
        require(isRunning) {
            "Security Manager is not running."
        }
        requireNotNull(innerSecurityManager) {
            "Security Manager has not been initialized."
        }
    }

    @Volatile
    @VisibleForTesting
    internal var innerSecurityManager: RestSecurityManager? = null

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
                        LifecycleCoordinatorName.forComponent<PermissionManagementService>(),
                    )
                )
            }
            is RegistrationStatusChangeEvent -> {
                log.info("Received registration status update ${event.status}.")
                when (event.status) {
                    LifecycleStatus.UP -> {
                        innerSecurityManager?.stop()
                        innerSecurityManager = RBACSecurityManager(
                            permissionManagementService::permissionValidator,
                            permissionManagementService.basicAuthenticationService
                        )
                        coordinator.updateStatus(LifecycleStatus.UP)
                    }
                    LifecycleStatus.DOWN -> {
                        downTransition()
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
                downTransition()
            }
        }
    }

    private fun downTransition() {
        innerSecurityManager?.stop()
        innerSecurityManager = null
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
