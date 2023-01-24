package net.corda.httprpc.server.factory

import net.corda.httprpc.PluggableRestResource
import net.corda.httprpc.RestResource
import net.corda.httprpc.security.read.RPCSecurityManager
import net.corda.httprpc.server.HttpRpcServer
import net.corda.httprpc.server.config.models.HttpRpcSettings
import java.nio.file.Path
import java.util.function.Supplier

interface HttpRpcServerFactory {

    fun createHttpRpcServer(
        restResourceImpls: List<PluggableRestResource<out RestResource>>,
        rpcSecurityManagerSupplier: Supplier<RPCSecurityManager>,
        httpRpcSettings: HttpRpcSettings,
        multiPartDir: Path,
        devMode: Boolean = false
    ): HttpRpcServer
}