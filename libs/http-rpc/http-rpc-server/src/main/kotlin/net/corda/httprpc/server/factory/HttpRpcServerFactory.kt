package net.corda.httprpc.server.factory

import net.corda.httprpc.security.read.RPCSecurityManager
import net.corda.httprpc.server.HttpRpcServer
import net.corda.httprpc.server.config.models.HttpRpcSettings
import net.corda.httprpc.Controller

interface HttpRpcServerFactory {

    fun createHttpRpcServer(
        controllers: List<Controller>,
        rpcSecurityManager: RPCSecurityManager,
        httpRpcSettings: HttpRpcSettings,
        devMode: Boolean
    ): HttpRpcServer
}