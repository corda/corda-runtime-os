package net.corda.httprpc.server.impl.factory

import net.corda.httprpc.security.read.RPCSecurityManager
import net.corda.httprpc.server.impl.RestServerImpl
import net.corda.httprpc.server.RestServer
import net.corda.httprpc.server.config.models.RestServerSettings
import net.corda.httprpc.server.factory.RestServerFactory
import net.corda.httprpc.PluggableRestResource
import net.corda.httprpc.RestResource
import org.osgi.service.component.annotations.Component
import java.nio.file.Path
import java.util.function.Supplier

@Component(immediate = true, service = [RestServerFactory::class])
@Suppress("Unused")
class RestServerFactoryImpl : RestServerFactory {

    override fun createRestServer(
        restResourceImpls: List<PluggableRestResource<out RestResource>>,
        restSecurityManagerSupplier: Supplier<RPCSecurityManager>,
        restServerSettings: RestServerSettings,
        multiPartDir: Path,
        devMode: Boolean
    ): RestServer {

        return RestServerImpl(restResourceImpls, restSecurityManagerSupplier, restServerSettings, multiPartDir, devMode = devMode)
    }
}