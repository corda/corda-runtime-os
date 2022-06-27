package net.corda.httprpc.server.factory

import net.corda.httprpc.PluggableRPCOps
import net.corda.httprpc.RpcOps
import net.corda.httprpc.security.read.RPCSecurityManager
import net.corda.httprpc.server.HttpRpcServer
import net.corda.httprpc.server.config.models.HttpRpcSettings
import net.corda.httprpc.server.security.local.HttpRpcLocalJwtSigner
import java.nio.file.Path

interface HttpRpcServerFactory {

    fun createHttpRpcServer(
        rpcOpsImpls: List<PluggableRPCOps<out RpcOps>>,
        rpcSecurityManager: RPCSecurityManager,
        httpRpcSettings: HttpRpcSettings,
        multiPartDir: Path,
        httpRpcLocalJwtSigner: HttpRpcLocalJwtSigner
    ): HttpRpcServer
}