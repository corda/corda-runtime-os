package net.corda.httprpc.server.impl

import net.corda.httprpc.PluggableRPCOps
import net.corda.httprpc.RpcOps
import net.corda.httprpc.security.read.RPCSecurityManager
import net.corda.httprpc.server.HttpRpcServer
import net.corda.httprpc.server.config.HttpRpcSettingsProvider
import net.corda.httprpc.server.config.impl.HttpRpcObjectSettingsProvider
import net.corda.httprpc.server.config.models.HttpRpcSettings
import net.corda.httprpc.server.impl.apigen.models.Resource
import net.corda.httprpc.server.impl.apigen.processing.APIStructureRetriever
import net.corda.httprpc.server.impl.apigen.processing.JavalinRouteProviderImpl
import net.corda.httprpc.server.impl.apigen.processing.openapi.OpenApiInfoProvider
import net.corda.httprpc.server.impl.internal.HttpRpcServerInternal
import net.corda.httprpc.server.impl.security.SecurityManagerRPCImpl
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
class HttpRpcServerImpl(
    rpcOpsImpls: List<PluggableRPCOps<out RpcOps>>,
    rpcSecurityManagerSupplier: Supplier<RPCSecurityManager>,
    httpRpcSettings: HttpRpcSettings,
    multiPartDir: Path,
    devMode: Boolean
) : HttpRpcServer {
    private companion object {
        private val log = contextLogger()
    }

    @Volatile
    private var running = false
    private val startStopLock = ReentrantReadWriteLock()

    private val resources = getResources(rpcOpsImpls)
    private val httpRpcObjectConfigProvider = HttpRpcObjectSettingsProvider(httpRpcSettings, devMode)

    private val httpRpcServerInternal = HttpRpcServerInternal(
        JavalinRouteProviderImpl(
            httpRpcSettings.context.basePath,
            httpRpcSettings.context.version,
            resources
        ),
        SecurityManagerRPCImpl(createAuthenticationProviders(httpRpcObjectConfigProvider, rpcSecurityManagerSupplier)),
        httpRpcObjectConfigProvider,
        OpenApiInfoProvider(resources, httpRpcObjectConfigProvider),
        multiPartDir,
        DeferredWebSocketCloserService()
    )

    override val port: Int
        get() = httpRpcServerInternal.port

    override fun start() {
        startStopLock.write {
            if (!running) {
                log.info("Started the server")
                httpRpcServerInternal.start()
                running = true
            }
        }
    }

    override fun close() {
        startStopLock.write {
            if (running) {
                log.info("Stop the server.")
                httpRpcServerInternal.stop()
                running = false
            }
        }
    }

    private fun getResources(rpcOpsImpls: List<PluggableRPCOps<out RpcOps>>): List<Resource> {
        log.debug { "Get resources for RPCOps implementations of ${rpcOpsImpls.joinToString()}." }
        log.trace { "Generating resource model for http rpc" }
        val resources = try {
            APIStructureRetriever(rpcOpsImpls).structure
        } catch (e: Exception) {
            "Error during Get resources for RPCOps implementations of ${rpcOpsImpls.joinToString()}".let { msg ->
                log.error("$msg: ${e.message}")
                throw Exception(msg, e)
            }
        }
        log.debug { "Http RPC resources count: ${resources.size}" }
        log.debug { "Get resources for RPCOps implementations of ${rpcOpsImpls.joinToString()} completed." }
        return resources
    }

    private fun createAuthenticationProviders(
        settings: HttpRpcSettingsProvider,
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
