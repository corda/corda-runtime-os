package net.corda.components.rest

import net.corda.components.rbac.RBACSecurityManagerService
import net.corda.components.rest.RestGateway.Companion.INTERNAL_PLUGGABLE_REST_RESOURCES
import net.corda.components.rest.internal.RestGatewayEventHandler
import net.corda.configuration.read.ConfigurationReadService
import net.corda.rest.PluggableRestResource
import net.corda.rest.RestResource
import net.corda.rest.server.factory.RestServerFactory
import net.corda.rest.ssl.SslCertReadServiceFactory
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.createCoordinator
import net.corda.permissions.management.PermissionManagementService
import org.osgi.service.component.ComponentContext
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ReferenceCardinality
import org.osgi.service.component.annotations.ReferencePolicy
import org.slf4j.LoggerFactory

@Suppress("LongParameterList")
@Component(
    service = [RestGateway::class],
    reference = [
        Reference(
            name = INTERNAL_PLUGGABLE_REST_RESOURCES,
            service = PluggableRestResource::class,
            cardinality = ReferenceCardinality.MULTIPLE,
            policy = ReferencePolicy.DYNAMIC
        )
    ])
class RestGateway @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    configurationReadService: ConfigurationReadService,
    @Reference(service = RestServerFactory::class)
    restServerFactory: RestServerFactory,
    @Reference(service = RBACSecurityManagerService::class)
    rbacSecurityManagerService: RBACSecurityManagerService,
    @Reference(service = SslCertReadServiceFactory::class)
    sslCertReadServiceFactory: SslCertReadServiceFactory,
    @Reference(service = PermissionManagementService::class)
    permissionManagementService: PermissionManagementService,
    private val componentContext: ComponentContext
) : Lifecycle {

     companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
        internal const val INTERNAL_PLUGGABLE_REST_RESOURCES = "internalPluggableRestResources"

         private fun <T> ComponentContext.fetchServices(refName: String): List<T> {
             @Suppress("unchecked_cast")
             return (locateServices(refName) as? Array<T>)?.toList() ?: emptyList()
         }
    }

    private val dynamicRestResources: List<PluggableRestResource<out RestResource>>
        get() = componentContext.fetchServices(INTERNAL_PLUGGABLE_REST_RESOURCES)

    private val handler = RestGatewayEventHandler(
        permissionManagementService,
        configurationReadService,
        restServerFactory,
        rbacSecurityManagerService,
        sslCertReadServiceFactory,
        ::dynamicRestResources
    )

    private var coordinator: LifecycleCoordinator = coordinatorFactory.createCoordinator<RestGateway>(handler)

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        coordinator.start()
    }

    override fun stop() {
        coordinator.stop()
    }
}