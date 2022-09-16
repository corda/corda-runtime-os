package net.corda.httprpc.server.factory

import net.corda.httprpc.PluggableRPCOps
import net.corda.httprpc.RpcOps
import net.corda.httprpc.security.read.RPCSecurityManager
import net.corda.httprpc.server.HttpRpcServer
import net.corda.httprpc.server.config.models.HttpRpcSettings
import java.nio.file.Path
import java.util.function.Supplier

interface HttpRpcServerFactory {

    fun createHttpRpcServer(
        rpcOpsImpls: List<PluggableRPCOps<out RpcOps>>,
        rpcSecurityManagerSupplier: Supplier<RPCSecurityManager>,
        httpRpcSettings: HttpRpcSettings,
        multiPartDir: Path,
        devMode: Boolean = false
    ): HttpRpcServer
}