package net.corda.components.rpc

import net.corda.components.rpc.internal.HttpRpcGatewayEventHandler
import net.corda.configuration.read.ConfigurationReadService
import net.corda.httprpc.PluggableRPCOps
import net.corda.httprpc.RpcOps
import net.corda.httprpc.security.read.RPCSecurityManagerFactory
import net.corda.httprpc.server.factory.HttpRpcServerFactory
import net.corda.httprpc.ssl.SslCertReadServiceFactory
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.createCoordinator
import net.corda.permissions.service.PermissionServiceComponent
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ReferenceCardinality
import org.osgi.service.component.annotations.ReferencePolicy

@Suppress("LongParameterList")
@Component(service = [HttpRpcGateway::class])
class HttpRpcGateway(
    coordinatorFactory: LifecycleCoordinatorFactory,
    configurationReadService: ConfigurationReadService,
    httpRpcServerFactory: HttpRpcServerFactory,
    rpcSecurityManagerFactory: RPCSecurityManagerFactory,
    sslCertReadServiceFactory: SslCertReadServiceFactory,
    permissionServiceComponent: PermissionServiceComponent,
    dynamicRpcOps: List<PluggableRPCOps<out RpcOps>>
) : Lifecycle {

    private companion object {
        val log = contextLogger()
    }

    @Reference(service = PluggableRPCOps::class, cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC)
    private val dynamicRpcOps: List<PluggableRPCOps<out RpcOps>> = dynamicRpcOps

    @Activate
    constructor(
        @Reference(service = LifecycleCoordinatorFactory::class)
        coordinatorFactory: LifecycleCoordinatorFactory,
        @Reference(service = ConfigurationReadService::class)
        configurationReadService: ConfigurationReadService,
        @Reference(service = HttpRpcServerFactory::class)
        httpRpcServerFactory: HttpRpcServerFactory,
        @Reference(service = RPCSecurityManagerFactory::class)
        rpcSecurityManagerFactory: RPCSecurityManagerFactory,
        @Reference(service = SslCertReadServiceFactory::class)
        sslCertReadServiceFactory: SslCertReadServiceFactory,
        @Reference(service = PermissionServiceComponent::class)
        permissionServiceComponent: PermissionServiceComponent,
    ) : this(
        coordinatorFactory,
        configurationReadService,
        httpRpcServerFactory,
        rpcSecurityManagerFactory,
        sslCertReadServiceFactory,
        permissionServiceComponent,
        mutableListOf()
    )

    private val handler = HttpRpcGatewayEventHandler(
        permissionServiceComponent,
        configurationReadService,
        httpRpcServerFactory,
        rpcSecurityManagerFactory,
        sslCertReadServiceFactory,
        this.dynamicRpcOps
    )

    private var coordinator: LifecycleCoordinator = coordinatorFactory.createCoordinator<HttpRpcGateway>(handler)

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        log.info("Starting lifecycle coordinator for RBAC permission system.")
        coordinator.start()
    }

    override fun stop() {
        coordinator.stop()
    }
}