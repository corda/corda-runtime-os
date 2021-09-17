package net.corda.httprpc.server.factory

import net.corda.httprpc.security.read.RPCSecurityManager
import net.corda.httprpc.server.HttpRpcServerImpl
import net.corda.httprpc.server.HttpRpcServer
import net.corda.httprpc.server.config.models.HttpRpcSettings
import net.corda.v5.httprpc.api.PluggableRPCOps
import net.corda.v5.httprpc.api.RpcOps
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.ServiceScope

@Component(service = [HttpRpcServerFactory::class], scope = ServiceScope.SINGLETON)
class HttpRpcServerFactoryImpl : HttpRpcServerFactory {

    override fun createHttpRpcServer(
        rpcOpsImpls: List<PluggableRPCOps<out RpcOps>>,
        rpcSecurityManager: RPCSecurityManager,
        httpRpcSettings: HttpRpcSettings,
        devMode: Boolean,
        cordappClassLoader: ClassLoader
    ): HttpRpcServer {

        return HttpRpcServerImpl(rpcOpsImpls, rpcSecurityManager, httpRpcSettings, devMode, cordappClassLoader)
    }
}