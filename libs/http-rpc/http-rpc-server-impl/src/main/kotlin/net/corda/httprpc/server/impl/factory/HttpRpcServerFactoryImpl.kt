package net.corda.httprpc.server.impl.factory

import net.corda.httprpc.security.read.RPCSecurityManager
import net.corda.httprpc.server.impl.HttpRpcServerImpl
import net.corda.httprpc.server.HttpRpcServer
import net.corda.httprpc.server.config.models.HttpRpcSettings
import net.corda.httprpc.server.factory.HttpRpcServerFactory
import net.corda.v5.httprpc.api.PluggableRPCOps
import net.corda.v5.httprpc.api.RpcOps
import org.osgi.service.component.annotations.Component

@Component(immediate = true, service = [HttpRpcServerFactory::class])
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