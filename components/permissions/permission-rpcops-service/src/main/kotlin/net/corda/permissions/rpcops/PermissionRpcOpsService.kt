package net.corda.permissions.rpcops

import net.corda.httprpc.RpcOps
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
    private val permissionServiceComponent: PermissionServiceComponent
) : Lifecycle {

    private val handler = PermissionRpcOpsServiceEventHandler(permissionServiceComponent)
    private val coordinator = coordinatorFactory.createCoordinator<PermissionRpcOpsService>(handler)

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        coordinator.start()
    }

    override fun stop() {
        coordinator.stop()
    }

    /**
     * Get a list of the Permission endpoints.
     */
    val rpcOps: List<RpcOps>
        get() {
            check(isRunning) {
                "Permission RPC Ops Service is not running."
            }
            checkNotNull(handler.userEndpoint) {
                "Permission RPC Ops Service is running but User endpoint is null."
            }
            return listOf(handler.userEndpoint!!)
        }
}