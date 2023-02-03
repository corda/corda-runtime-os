package net.corda.httprpc.client.connect

import net.corda.httprpc.ResponseCode
import net.corda.httprpc.RestResource
import net.corda.httprpc.annotations.HttpDELETE
import net.corda.httprpc.annotations.HttpGET
import net.corda.httprpc.annotations.HttpPOST
import net.corda.httprpc.annotations.HttpPUT
import net.corda.httprpc.annotations.HttpRestResource
import net.corda.httprpc.annotations.RestSinceVersion
import net.corda.httprpc.annotations.isRestEndpointAnnotation
import net.corda.httprpc.client.auth.RequestContext
import net.corda.httprpc.client.config.AuthenticationConfig
import net.corda.httprpc.client.connect.remote.RemoteClient
import net.corda.httprpc.client.connect.stream.RestFiniteDurableCursorClientBuilderImpl
import net.corda.httprpc.client.exceptions.InternalErrorException
import net.corda.httprpc.client.processing.endpointHttpVerb
import net.corda.httprpc.client.processing.parametersFrom
import net.corda.httprpc.client.processing.toWebRequest
import net.corda.httprpc.durablestream.api.returnsDurableCursorBuilder
import net.corda.httprpc.response.ResponseEntity
import net.corda.httprpc.tools.HttpPathUtils.joinResourceAndEndpointPaths
import net.corda.httprpc.tools.annotations.extensions.path
import net.corda.httprpc.tools.isStaticallyExposedGet
import net.corda.v5.base.util.trace
import org.slf4j.LoggerFactory
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType

/**
 * [RestClientProxyHandler] is responsible for converting method invocations to web requests that are called against the server,
 * using the provided client.
 *
 * @param I The proxied interface.
 * @property client The client to use for the remote calls.
 * @property restResourceClass The proxied interface class.
 */
internal class RestClientProxyHandler<I : RestResource>(
    private val client: RemoteClient,
    private val authenticationConfig: AuthenticationConfig,
    private val restResourceClass: Class<I>
) : InvocationHandler {

    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
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
            val sinceVersion = method.getAnnotation(RestSinceVersion::class.java)?.version ?: 0
            if (sinceVersion > serverProtocolVersion) {
                throw UnsupportedOperationException(
                    "Method $method was added in protocol version '$sinceVersion' " +
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
            if (method.annotations.none { it.isRestEndpointAnnotation() }) {
                throw UnsupportedOperationException(
                    "REST client proxy can not make remote calls for functions not annotated or known as implicitly exposed."
                )
            }

            checkServerProtocolVersion(method)
        }

        val resourcePath = restResourceClass.getAnnotation(HttpRestResource::class.java)?.path(restResourceClass)
            ?: throw UnsupportedOperationException(
                "REST client proxy can not make remote calls for interfaces not annotated with HttpRestResource."
            )

        val rawPath = joinResourceAndEndpointPaths(resourcePath, method.endpointPath).lowercase()

        if (method.returnsDurableCursorBuilder()) {
            return RestFiniteDurableCursorClientBuilderImpl(
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
            this.annotations.singleOrNull { it.isRestEndpointAnnotation() }.let {
                when (it) {
                    is HttpGET -> it.path(this)
                    is HttpPOST -> it.path()
                    is HttpPUT -> it.path()
                    is HttpDELETE -> it.path()
                    else -> if (isStaticallyExposedGet()) {
                        this.name
                    } else {
                        throw IllegalArgumentException("Unknown endpoint path for: '$name'")
                    }
                }
            }
}
