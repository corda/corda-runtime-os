package net.corda.permissions.rpcops

import net.corda.libs.permissions.endpoints.v1.user.UserEndpoint
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.createCoordinator
import net.corda.permissions.rpcops.internal.PermissionRpcOpsServiceEventHandler
import net.corda.permissions.service.PermissionServiceComponent
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [PermissionRpcOpsService::class])
class PermissionRpcOpsService @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = PermissionServiceComponent::class)
    private val permissionServiceComponent: PermissionServiceComponent,
    @Reference(service = UserEndpoint::class)
    private val userEndpoint: UserEndpoint
) : Lifecycle {

    private val handler = PermissionRpcOpsServiceEventHandler(permissionServiceComponent, userEndpoint)
    private val coordinator = coordinatorFactory.createCoordinator<PermissionRpcOpsService>(handler)

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        coordinator.start()
    }

    override fun stop() {
        coordinator.stop()
    }
}