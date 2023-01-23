package net.corda.httprpc.server.impl.factory

import net.corda.httprpc.security.read.RPCSecurityManager
import net.corda.httprpc.server.impl.HttpRpcServerImpl
import net.corda.httprpc.server.HttpRpcServer
import net.corda.httprpc.server.config.models.HttpRpcSettings
import net.corda.httprpc.server.factory.HttpRpcServerFactory
import net.corda.httprpc.PluggableRestResource
import net.corda.httprpc.RestResource
import org.osgi.service.component.annotations.Component
import java.nio.file.Path
import java.util.function.Supplier

@Component(immediate = true, service = [HttpRpcServerFactory::class])
@Suppress("Unused")
class HttpRpcServerFactoryImpl : HttpRpcServerFactory {

    override fun createHttpRpcServer(
        restResourceImpls: List<PluggableRestResource<out RestResource>>,
        rpcSecurityManagerSupplier: Supplier<RPCSecurityManager>,
        httpRpcSettings: HttpRpcSettings,
        multiPartDir: Path,
        devMode: Boolean
    ): HttpRpcServer {

        return HttpRpcServerImpl(restResourceImpls, rpcSecurityManagerSupplier, httpRpcSettings, multiPartDir, devMode = devMode)
    }
}