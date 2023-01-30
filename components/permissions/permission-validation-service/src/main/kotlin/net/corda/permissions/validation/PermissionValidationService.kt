package net.corda.permissions.validation

import net.corda.libs.permission.PermissionValidator
import net.corda.libs.permission.factory.PermissionValidatorFactory
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.permissions.validation.cache.PermissionValidationCacheService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory

/**
 * Service for performing permission validation using the RBAC permission system.
 *
 * The service exposes the PermissionValidator API for authorizing a request using the RBAC system for the requesting user.
 *
 * To use the Permission Validator to authorize a user, dependency inject this service using OSGI and start the service. The service will
 * start all necessary permission related dependencies and the above APIs can be used to interact with the system.
 */
@Component(service = [PermissionValidationService::class])
class PermissionValidationService @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = PermissionValidatorFactory::class)
    private val permissionValidatorFactory: PermissionValidatorFactory,
    @Reference(service = PermissionValidationCacheService::class)
    private val permissionValidationCacheService: PermissionValidationCacheService
) : Lifecycle {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val coordinator = coordinatorFactory.createCoordinator<PermissionValidationService> { event, _ -> eventHandler(event) }

    private var registration: RegistrationHandle? = null

    /**
     * Permission validator for performing user authorization against the given permission using the RBAC permission system.
     */
    val permissionValidator: PermissionValidator
        get() {
            return checkNotNull(_permissionValidator) {
                "Permission Validator is null. Getter should be called only after service is UP."
            }
        }

    private fun eventHandler(event: LifecycleEvent) {
        when (event) {
            is StartEvent -> {
                log.info("Received start event, following validation cache service for status updates.")
                registration?.close()
                registration = coordinator.followStatusChangesByName(
                    setOf(
                        LifecycleCoordinatorName.forComponent<PermissionValidationCacheService>()
                    )
                )
                permissionValidationCacheService.start()
            }
            is RegistrationStatusChangeEvent -> {
                log.info("Registration status change received: ${event.status.name}.")
                if (event.status == LifecycleStatus.UP) {
                    startValidationComponent()
                    coordinator.updateStatus(LifecycleStatus.UP)
                } else {
                    coordinator.stop()
                }
            }
            is StopEvent -> {
                log.info("Stop event received, stopping dependencies.")
                permissionValidationCacheService.stop()
                _permissionValidator?.stop()
                _permissionValidator = null
                registration?.close()
                registration = null
            }
        }
    }

    private fun startValidationComponent() {
        _permissionValidator?.stop()
        _permissionValidator = permissionValidatorFactory.create(permissionValidationCacheService.permissionValidationCacheRef)
            .also { it.start() }
    }

    private var _permissionValidator: PermissionValidator? = null

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        coordinator.start()
    }

    override fun stop() {
        coordinator.stop()
    }
}
