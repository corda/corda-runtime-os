package net.corda.rest.server.impl

import net.corda.rest.PluggableRestResource
import net.corda.rest.RestResource
import net.corda.rest.annotations.RestApiVersion
import net.corda.rest.security.read.RestSecurityManager
import net.corda.rest.server.RestServer
import net.corda.rest.server.config.RestServerSettingsProvider
import net.corda.rest.server.config.impl.RestServerObjectSettingsProvider
import net.corda.rest.server.config.models.RestServerSettings
import net.corda.rest.server.impl.apigen.models.Resource
import net.corda.rest.server.impl.apigen.processing.APIStructureRetriever
import net.corda.rest.server.impl.apigen.processing.JavalinRouteProviderImpl
import net.corda.rest.server.impl.apigen.processing.openapi.OpenApiInfoProvider
import net.corda.rest.server.impl.internal.RestServerInternal
import net.corda.rest.server.impl.security.RestAuthenticationProviderImpl
import net.corda.rest.server.impl.security.provider.AuthenticationProvider
import net.corda.rest.server.impl.security.provider.basic.UsernamePasswordAuthenticationProvider
import net.corda.rest.server.impl.security.provider.bearer.azuread.AzureAdAuthenticationProvider
import net.corda.rest.server.impl.websocket.deferred.DeferredWebSocketCloserService
import net.corda.utilities.debug
import net.corda.utilities.trace
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.function.Supplier
import kotlin.concurrent.write

@SuppressWarnings("TooGenericExceptionThrown", "LongParameterList")
class RestServerImpl(
    restResourceImpls: List<PluggableRestResource<out RestResource>>,
    restSecurityManagerSupplier: Supplier<RestSecurityManager>,
    restServerSettings: RestServerSettings,
    multiPartDir: Path,
    devMode: Boolean
) : RestServer {
    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @Volatile
    private var running = false
    private val startStopLock = ReentrantReadWriteLock()

    private val resources = getResources(restResourceImpls)
    private val restServerConfigProvider = RestServerObjectSettingsProvider(restServerSettings, devMode)

    private val restServerInternal = RestServerInternal(
        JavalinRouteProviderImpl(
            restServerSettings.context.basePath,
            resources
        ),
        RestAuthenticationProviderImpl(createAuthenticationProviders(restServerConfigProvider, restSecurityManagerSupplier)),
        restServerConfigProvider,
        createOpenApiInfoProviders(resources, restServerConfigProvider),
        multiPartDir,
        DeferredWebSocketCloserService()
    )

    private fun createOpenApiInfoProviders(
        resources: List<Resource>,
        restServerConfigProvider: RestServerObjectSettingsProvider
    ): List<OpenApiInfoProvider> {
        return RestApiVersion.values()
            .map { apiVersion -> OpenApiInfoProvider(resources, restServerConfigProvider, apiVersion) }
    }

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
        restSecurityManagerSupplier: Supplier<RestSecurityManager>
    ): Set<AuthenticationProvider> {
        val result = mutableSetOf<AuthenticationProvider>(UsernamePasswordAuthenticationProvider(restSecurityManagerSupplier))
        val azureAdSettings = settings.getSsoSettings()?.azureAd()
        if (azureAdSettings != null) {
            result.add(AzureAdAuthenticationProvider.createDefault(azureAdSettings, restSecurityManagerSupplier))
        }
        return result
    }
}
