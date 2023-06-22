package net.corda.permissions.management

import net.corda.permissions.management.internal.InteropManagementServiceEventHandler
import net.corda.libs.interop.endpoints.v1.InteropManager
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.createCoordinator
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/**
 * Service for managing interop in the system.
 *
 * The service exposes the following APIs:
 * - InteropManager - API for managing interop.
 *
 * To use the Interop Management Service, dependency inject the service using OSGI and start the service. The service will start all
 * necessary interop related dependencies and the above APIs can be used to interact with the system.
 *
 */
@Suppress("LongParameterList")
@Component(service = [InteropManagementService::class])
class InteropManagementService @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
) : Lifecycle {

    private val handler = InteropManagementServiceEventHandler()
    private val coordinator = coordinatorFactory.createCoordinator<InteropManagementService>(handler)

    /**
     * Manager for performing interop management operations.
     */
    val interopManager: InteropManager
        get() {
            return checkNotNull(handler.interopManager) {
                "Interop Manager is null. Getter should be called only after service is UP."
            }
        }

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        coordinator.start()
    }

    override fun stop() {
        coordinator.stop()
    }
}