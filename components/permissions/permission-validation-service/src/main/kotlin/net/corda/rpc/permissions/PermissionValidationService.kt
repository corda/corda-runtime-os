package net.corda.rpc.permissions

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
import net.corda.permissions.cache.PermissionCacheService
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [PermissionValidationService::class])
class PermissionValidationService @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = PermissionValidatorFactory::class)
    private val permissionValidatorFactory: PermissionValidatorFactory,
    @Reference(service = PermissionCacheService::class)
    private val permissionCacheService: PermissionCacheService,
) : Lifecycle {

    private companion object {
        val log = contextLogger()
    }

    private val coordinator = coordinatorFactory.createCoordinator<PermissionValidationService> { event, _ -> eventHandler(event) }

    private var registration: RegistrationHandle? = null

    val permissionValidator: PermissionValidator
        get() {
            checkNotNull(_permissionValidator) {
                "Permission Validator is null. Getter should be called only after service is UP."
            }
            return _permissionValidator!!
        }

    private fun eventHandler(event: LifecycleEvent) {
        when (event) {
            is StartEvent -> {
                log.info("Received start event, following PermissionCacheService for status updates.")
                registration?.close()
                registration = coordinator.followStatusChangesByName(
                    setOf(
                        LifecycleCoordinatorName.forComponent<PermissionCacheService>()
                    )
                )
            }
            is RegistrationStatusChangeEvent -> {
                log.info("Registration status change received: PermissionCacheService ${event.status.name}.")
                if (event.status == LifecycleStatus.UP) {
                    startValidationComponent()
                    coordinator.updateStatus(LifecycleStatus.UP)
                } else {
                    coordinator.stop()
                }
            }
            is StopEvent -> {
                log.info("Stop event received, stopping dependencies and setting status to DOWN.")
                _permissionValidator?.stop()
                _permissionValidator = null
                registration?.close()
                registration = null
                coordinator.updateStatus(LifecycleStatus.DOWN)
            }
        }
    }

    private fun startValidationComponent() {
        val permissionCache = permissionCacheService.permissionCache
        checkNotNull(permissionCache) {
            "The PermissionCacheService reported status UP but its permissionCache field was null."
        }
        _permissionValidator = permissionValidatorFactory.createPermissionValidator(permissionCache)
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