package net.corda.httprpc.server.factory

import net.corda.httprpc.security.read.RPCSecurityManager
import net.corda.httprpc.server.HttpRpcServer
import net.corda.httprpc.server.config.models.HttpRpcSettings
import net.corda.v5.httprpc.api.Controller
import net.corda.v5.httprpc.api.PluggableRPCOps
import net.corda.v5.httprpc.api.RpcOps

interface HttpRpcServerFactory {

    fun createHttpRpcServer(
        controllers: List<Controller>,
        rpcSecurityManager: RPCSecurityManager,
        httpRpcSettings: HttpRpcSettings,
        devMode: Boolean
    ): HttpRpcServer
}