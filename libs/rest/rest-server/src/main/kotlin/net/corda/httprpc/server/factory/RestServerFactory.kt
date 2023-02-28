package net.corda.rest.server.factory

import net.corda.rest.PluggableRestResource
import net.corda.rest.RestResource
import net.corda.rest.security.read.RestSecurityManager
import net.corda.rest.server.RestServer
import net.corda.rest.server.config.models.RestServerSettings
import java.nio.file.Path
import java.util.function.Supplier

interface RestServerFactory {

    fun createRestServer(
        restResourceImpls: List<PluggableRestResource<out RestResource>>,
        restSecurityManagerSupplier: Supplier<RestSecurityManager>,
        restServerSettings: RestServerSettings,
        multiPartDir: Path,
        devMode: Boolean = false
    ): RestServer
}