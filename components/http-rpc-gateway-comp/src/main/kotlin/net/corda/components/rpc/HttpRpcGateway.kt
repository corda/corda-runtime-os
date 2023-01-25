package net.corda.components.rpc

import net.corda.components.rbac.RBACSecurityManagerService
import net.corda.components.rpc.HttpRpcGateway.Companion.INTERNAL_PLUGGABLE_RPC_OPS
import net.corda.components.rpc.internal.HttpRpcGatewayEventHandler
import net.corda.configuration.read.ConfigurationReadService
import net.corda.httprpc.PluggableRestResource
import net.corda.httprpc.RestResource
import net.corda.httprpc.server.factory.HttpRpcServerFactory
import net.corda.httprpc.ssl.SslCertReadServiceFactory
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.createCoordinator
import net.corda.permissions.management.PermissionManagementService
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.ComponentContext
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ReferenceCardinality
import org.osgi.service.component.annotations.ReferencePolicy

@Suppress("LongParameterList")
@Component(
    service = [HttpRpcGateway::class],
    reference = [
        Reference(
            name = INTERNAL_PLUGGABLE_RPC_OPS,
            service = PluggableRestResource::class,
            cardinality = ReferenceCardinality.MULTIPLE,
            policy = ReferencePolicy.DYNAMIC
        )
    ])
class HttpRpcGateway @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    configurationReadService: ConfigurationReadService,
    @Reference(service = HttpRpcServerFactory::class)
    httpRpcServerFactory: HttpRpcServerFactory,
    @Reference(service = RBACSecurityManagerService::class)
    rbacSecurityManagerService: RBACSecurityManagerService,
    @Reference(service = SslCertReadServiceFactory::class)
    sslCertReadServiceFactory: SslCertReadServiceFactory,
    @Reference(service = PermissionManagementService::class)
    permissionManagementService: PermissionManagementService,
    private val componentContext: ComponentContext
) : Lifecycle {

     companion object {
        private val log = contextLogger()
        internal const val INTERNAL_PLUGGABLE_RPC_OPS = "internalPluggableRpcOps"

         private fun <T> ComponentContext.fetchServices(refName: String): List<T> {
             @Suppress("unchecked_cast")
             return (locateServices(refName) as? Array<T>)?.toList() ?: emptyList()
         }
    }

    private val dynamicRestResources: List<PluggableRestResource<out RestResource>>
        get() = componentContext.fetchServices(INTERNAL_PLUGGABLE_RPC_OPS)

    private val handler = HttpRpcGatewayEventHandler(
        permissionManagementService,
        configurationReadService,
        httpRpcServerFactory,
        rbacSecurityManagerService,
        sslCertReadServiceFactory,
        ::dynamicRestResources
    )

    private var coordinator: LifecycleCoordinator = coordinatorFactory.createCoordinator<HttpRpcGateway>(handler)

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        coordinator.start()
    }

    override fun stop() {
        coordinator.stop()
    }
}