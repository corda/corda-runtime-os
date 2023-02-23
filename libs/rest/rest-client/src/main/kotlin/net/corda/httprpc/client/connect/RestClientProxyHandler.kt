package net.corda.rest.client.connect

import net.corda.rest.ResponseCode
import net.corda.rest.RestResource
import net.corda.rest.annotations.HttpDELETE
import net.corda.rest.annotations.HttpGET
import net.corda.rest.annotations.HttpPOST
import net.corda.rest.annotations.HttpPUT
import net.corda.rest.annotations.HttpRestResource
import net.corda.rest.annotations.RestSinceVersion
import net.corda.rest.annotations.isRestEndpointAnnotation
import net.corda.rest.client.auth.RequestContext
import net.corda.rest.client.config.AuthenticationConfig
import net.corda.rest.client.connect.remote.RemoteClient
import net.corda.rest.client.connect.stream.RestFiniteDurableCursorClientBuilderImpl
import net.corda.rest.client.exceptions.InternalErrorException
import net.corda.rest.client.processing.endpointHttpVerb
import net.corda.rest.client.processing.parametersFrom
import net.corda.rest.client.processing.toWebRequest
import net.corda.rest.durablestream.api.returnsDurableCursorBuilder
import net.corda.rest.response.ResponseEntity
import net.corda.rest.tools.HttpPathUtils.joinResourceAndEndpointPaths
import net.corda.rest.tools.annotations.extensions.path
import net.corda.rest.tools.isStaticallyExposedGet
import net.corda.utilities.trace
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
