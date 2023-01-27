package net.corda.httprpc.server.impl

import net.corda.httprpc.PluggableRestResource
import net.corda.httprpc.RestResource
import net.corda.httprpc.security.read.RPCSecurityManager
import net.corda.httprpc.server.RestServer
import net.corda.httprpc.server.config.RestServerSettingsProvider
import net.corda.httprpc.server.config.impl.RestServerObjectSettingsProvider
import net.corda.httprpc.server.config.models.RestServerSettings
import net.corda.httprpc.server.impl.apigen.models.Resource
import net.corda.httprpc.server.impl.apigen.processing.APIStructureRetriever
import net.corda.httprpc.server.impl.apigen.processing.JavalinRouteProviderImpl
import net.corda.httprpc.server.impl.apigen.processing.openapi.OpenApiInfoProvider
import net.corda.httprpc.server.impl.internal.RestServerInternal
import net.corda.httprpc.server.impl.security.RestAuthenticationProviderImpl
import net.corda.httprpc.server.impl.security.provider.AuthenticationProvider
import net.corda.httprpc.server.impl.security.provider.basic.UsernamePasswordAuthenticationProvider
import net.corda.httprpc.server.impl.security.provider.bearer.azuread.AzureAdAuthenticationProvider
import net.corda.httprpc.server.impl.websocket.deferred.DeferredWebSocketCloserService
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.v5.base.util.trace
import java.nio.file.Path
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.function.Supplier
import kotlin.concurrent.write

@SuppressWarnings("TooGenericExceptionThrown", "LongParameterList")
class RestServerImpl(
    restResourceImpls: List<PluggableRestResource<out RestResource>>,
    rpcSecurityManagerSupplier: Supplier<RPCSecurityManager>,
    restServerSettings: RestServerSettings,
    multiPartDir: Path,
    devMode: Boolean
) : RestServer {
    private companion object {
        private val log = contextLogger()
    }

    @Volatile
    private var running = false
    private val startStopLock = ReentrantReadWriteLock()

    private val resources = getResources(restResourceImpls)
    private val restServerConfigProvider = RestServerObjectSettingsProvider(restServerSettings, devMode)

    private val restServerInternal = RestServerInternal(
        JavalinRouteProviderImpl(
            restServerSettings.context.basePath,
            restServerSettings.context.version,
            resources
        ),
        RestAuthenticationProviderImpl(createAuthenticationProviders(restServerConfigProvider, rpcSecurityManagerSupplier)),
        restServerConfigProvider,
        OpenApiInfoProvider(resources, restServerConfigProvider),
        multiPartDir,
        DeferredWebSocketCloserService()
    )

    override val port: Int
        get() = restServerInternal.port

    override fun start() {
        startStopLock.write {
            if (!running) {
                log.info("Started the server")
                restServerInternal.start()
                running = true
            }
        }
    }

    override fun close() {
        startStopLock.write {
            if (running) {
                log.info("Stop the server.")
                restServerInternal.stop()
                running = false
            }
        }
    }

    private fun getResources(restResourceImpls: List<PluggableRestResource<out RestResource>>): List<Resource> {
        log.debug { "Get resources for RestResource implementations of ${restResourceImpls.joinToString()}." }
        log.trace { "Generating resource model for http rest" }
        val resources = try {
            APIStructureRetriever(restResourceImpls).structure
        } catch (e: Exception) {
            "Error during Get resources for RestResource implementations of ${restResourceImpls.joinToString()}".let { msg ->
                log.error("$msg: ${e.message}")
                throw Exception(msg, e)
            }
        }
        log.debug { "Http RestResource count: ${resources.size}" }
        log.debug { "Get resources for RestResource implementations of ${restResourceImpls.joinToString()} completed." }
        return resources
    }

    private fun createAuthenticationProviders(
        settings: RestServerSettingsProvider,
        rpcSecurityManagerSupplier: Supplier<RPCSecurityManager>
    ): Set<AuthenticationProvider> {
        val result = mutableSetOf<AuthenticationProvider>(UsernamePasswordAuthenticationProvider(rpcSecurityManagerSupplier))
        val azureAdSettings = settings.getSsoSettings()?.azureAd()
        if (azureAdSettings != null) {
            result.add(AzureAdAuthenticationProvider.createDefault(azureAdSettings, rpcSecurityManagerSupplier))
        }
        return result
    }
}
