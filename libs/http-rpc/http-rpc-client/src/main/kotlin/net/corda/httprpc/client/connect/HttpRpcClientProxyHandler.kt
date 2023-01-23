package net.corda.httprpc.client.connect

import net.corda.httprpc.RestResource
import net.corda.httprpc.annotations.HttpRpcDELETE
import net.corda.httprpc.annotations.HttpRpcGET
import net.corda.httprpc.annotations.HttpRpcPOST
import net.corda.httprpc.annotations.HttpRpcPUT
import net.corda.httprpc.annotations.HttpRpcResource
import net.corda.httprpc.annotations.RPCSinceVersion
import net.corda.httprpc.annotations.isRpcEndpointAnnotation
import net.corda.httprpc.client.auth.RequestContext
import net.corda.httprpc.client.config.AuthenticationConfig
import net.corda.httprpc.client.connect.remote.RemoteClient
import net.corda.httprpc.client.connect.stream.HttpRpcFiniteDurableCursorClientBuilderImpl
import net.corda.httprpc.client.processing.endpointHttpVerb
import net.corda.httprpc.client.processing.parametersFrom
import net.corda.httprpc.client.processing.toWebRequest
import net.corda.httprpc.tools.HttpPathUtils.joinResourceAndEndpointPaths
import net.corda.httprpc.tools.annotations.extensions.path
import net.corda.httprpc.tools.isStaticallyExposedGet
import net.corda.httprpc.durablestream.api.returnsDurableCursorBuilder
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.trace
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import net.corda.httprpc.ResponseCode
import net.corda.httprpc.client.exceptions.InternalErrorException
import net.corda.httprpc.response.ResponseEntity

/**
 * [HttpRpcClientProxyHandler] is responsible for converting method invocations to web requests that are called against the server,
 * using the provided client.
 *
 * @param I The proxied interface.
 * @property client The client to use for the remote calls.
 * @property rpcOpsClass The proxied interface class.
 */
internal class HttpRpcClientProxyHandler<I : RestResource>(
    private val client: RemoteClient,
    private val authenticationConfig: AuthenticationConfig,
    private val rpcOpsClass: Class<I>
) : InvocationHandler {

    private companion object {
        private val log = contextLogger()
    }

    private var serverProtocolVersion: Int? = null

    fun setServerProtocolVersion(version: Int) {
        if (serverProtocolVersion == null) {
            serverProtocolVersion = version
        } else {
            throw IllegalStateException("setServerProtocolVersion called, but the protocol version was already set!")
        }
    }

    private fun checkServerProtocolVersion(method: Method) {
        val serverProtocolVersion = serverProtocolVersion
        if (serverProtocolVersion == null) {
            log.warn("Server protocol version is not set in the proxy, can not verify server version compatibility.")
        } else {
            val sinceVersion = method.getAnnotation(RPCSinceVersion::class.java)?.version ?: 0
            if (sinceVersion > serverProtocolVersion) {
                throw UnsupportedOperationException(
                    "Method $method was added in RPC protocol version '$sinceVersion' " +
                            "but the server is running '$serverProtocolVersion'."
                )
            }
        }
    }

    @Suppress("ComplexMethod")
    override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any? {
        log.trace { """Invoke "${method.name}".""" }
        val isExemptFromChecks = method.isStaticallyExposedGet()
        if (!isExemptFromChecks) {
            if (method.annotations.none { it.isRpcEndpointAnnotation() }) {
                throw UnsupportedOperationException(
                    "Http RPC proxy can not make remote calls for functions not annotated or known as implicitly exposed."
                )
            }

            checkServerProtocolVersion(method)
        }

        val resourcePath = rpcOpsClass.getAnnotation(HttpRpcResource::class.java)?.path(rpcOpsClass)
            ?: throw UnsupportedOperationException(
                "Http RPC proxy can not make remote calls for interfaces not annotated with HttpRpcResource."
            )

        val rawPath = joinResourceAndEndpointPaths(resourcePath, method.endpointPath).lowercase()

        if (method.returnsDurableCursorBuilder()) {
            return HttpRpcFiniteDurableCursorClientBuilderImpl(
                client, method, rawPath, args, authenticationConfig
            ).also { log.trace { """Invoke "${method.name}" completed.""" } }
        }

        val parameters = method.parametersFrom(args)
        val context = RequestContext.fromAuthenticationConfig(authenticationConfig)
        return when {
            (method.returnType.isAssignableFrom(Void::class.java) || method.returnType.isAssignableFrom(Void.TYPE)) -> {
                client.call(method.endpointHttpVerb, parameters.toWebRequest(rawPath), context)
                null
            }
            method.returnType == String::class.java -> {
                client.call(method.endpointHttpVerb, parameters.toWebRequest(rawPath), context).body
            }
            method.returnType == ResponseEntity::class.java -> {
                val methodParameterizedType = method.genericReturnType as ParameterizedType
                val itemType = methodParameterizedType.actualTypeArguments[0]

                val response = client.call(
                    method.endpointHttpVerb,
                    parameters.toWebRequest(rawPath),
                    itemType,
                    context
                )
                ResponseEntity(response.responseStatus.toResponseCode(), response.body)
            }
            else -> {
                client.call(method.endpointHttpVerb, parameters.toWebRequest(rawPath), method.genericReturnType, context).body
            }
        }.also { log.trace { """Invoke "${method.name}" completed.""" } }
    }

    private fun Int.toResponseCode() = ResponseCode.values().find { it.statusCode == this }
        ?: throw InternalErrorException("Status code $this not implemented")

    private val Method.endpointPath: String?
        get() =
            this.annotations.singleOrNull { it.isRpcEndpointAnnotation() }.let {
                when (it) {
                    is HttpRpcGET -> it.path(this)
                    is HttpRpcPOST -> it.path()
                    is HttpRpcPUT -> it.path()
                    is HttpRpcDELETE -> it.path()
                    else -> if (isStaticallyExposedGet()) {
                        this.name
                    } else {
                        throw IllegalArgumentException("Unknown endpoint path for: '$name'")
                    }
                }
            }
}
