package net.corda.httprpc.server.factory

import net.corda.httprpc.PluggableRestResource
import net.corda.httprpc.RestResource
import net.corda.httprpc.security.read.RestSecurityManager
import net.corda.httprpc.server.RestServer
import net.corda.httprpc.server.config.models.RestServerSettings
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