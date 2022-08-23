package net.corda.httprpc.server.impl.factory

import net.corda.httprpc.security.read.RPCSecurityManager
import net.corda.httprpc.server.impl.HttpRpcServerImpl
import net.corda.httprpc.server.HttpRpcServer
import net.corda.httprpc.server.config.models.HttpRpcSettings
import net.corda.httprpc.server.factory.HttpRpcServerFactory
import net.corda.httprpc.PluggableRPCOps
import net.corda.httprpc.RpcOps
import org.osgi.service.component.annotations.Component
import java.nio.file.Path
import java.util.function.Supplier

@Component(immediate = true, service = [HttpRpcServerFactory::class])
@Suppress("Unused")
class HttpRpcServerFactoryImpl : HttpRpcServerFactory {

    override fun createHttpRpcServer(
        rpcOpsImpls: List<PluggableRPCOps<out RpcOps>>,
        rpcSecurityManagerSupplier: Supplier<RPCSecurityManager>,
        httpRpcSettings: HttpRpcSettings,
        multiPartDir: Path,
        devMode: Boolean
    ): HttpRpcServer {

        return HttpRpcServerImpl(rpcOpsImpls, rpcSecurityManagerSupplier, httpRpcSettings, multiPartDir, devMode = devMode)
    }
}