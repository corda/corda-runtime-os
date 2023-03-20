package net.corda.rest.server.impl.factory

import net.corda.rest.security.read.RestSecurityManager
import net.corda.rest.server.impl.RestServerImpl
import net.corda.rest.server.RestServer
import net.corda.rest.server.config.models.RestServerSettings
import net.corda.rest.server.factory.RestServerFactory
import net.corda.rest.PluggableRestResource
import net.corda.rest.RestResource
import org.osgi.service.component.annotations.Component
import java.nio.file.Path
import java.util.function.Supplier

@Component(immediate = true, service = [RestServerFactory::class])
@Suppress("Unused")
class RestServerFactoryImpl : RestServerFactory {

    override fun createRestServer(
        restResourceImpls: List<PluggableRestResource<out RestResource>>,
        restSecurityManagerSupplier: Supplier<RestSecurityManager>,
        restServerSettings: RestServerSettings,
        multiPartDir: Path,
        devMode: Boolean
    ): RestServer {

        return RestServerImpl(restResourceImpls, restSecurityManagerSupplier, restServerSettings, multiPartDir, devMode = devMode)
    }
}