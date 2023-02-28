package net.corda.httprpc.client.connect.remote

import kong.unirest.GenericType
import kong.unirest.HttpRequest
import kong.unirest.HttpRequestWithBody
import kong.unirest.HttpResponse
import kong.unirest.HttpStatus
import kong.unirest.MultipartBody
import kong.unirest.Unirest
import kong.unirest.UnirestException
import kong.unirest.apache.ApacheClient
import kong.unirest.jackson.JacksonObjectMapper
import net.corda.httprpc.client.auth.RequestContext
import net.corda.httprpc.client.exceptions.InternalErrorException
import net.corda.httprpc.client.exceptions.MissingRequestedResourceException
import net.corda.httprpc.client.exceptions.PermissionException
import net.corda.httprpc.client.exceptions.RequestErrorException
import net.corda.httprpc.client.processing.RestClientFileUpload
import net.corda.httprpc.client.processing.WebRequest
import net.corda.httprpc.client.processing.WebResponse
import net.corda.httprpc.client.serialization.objectMapper
import net.corda.httprpc.exception.ResourceAlreadyExistsException
import net.corda.httprpc.tools.HttpVerb
import net.corda.utilities.trace
import org.apache.http.conn.ssl.NoopHostnameVerifier
import org.apache.http.conn.ssl.TrustAllStrategy
import org.apache.http.impl.client.HttpClients
import org.apache.http.ssl.SSLContexts
import org.slf4j.LoggerFactory
import java.lang.reflect.Type
import javax.net.ssl.SSLContext

/**
 * [RemoteClient] implementations are responsible for making remote calls to the server and returning the response,
 * after potentially deserializing.
 *
 */
internal interface RemoteClient {
    fun <T> call(verb: HttpVerb, webRequest: WebRequest<T>, responseType: Type, context: RequestContext): WebResponse<Any>
    fun <T> call(verb: HttpVerb, webRequest: WebRequest<T>, context: RequestContext): WebResponse<String>
    val baseAddress: String
}

internal class RemoteUnirestClient(override val baseAddress: String, private val enableSsl: Boolean = false) : RemoteClient {
    internal companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    init {
        Unirest.config().objectMapper = JacksonObjectMapper(objectMapper)
    }

    override fun <T> call(verb: HttpVerb, webRequest: WebRequest<T>, responseType: Type, context: RequestContext): WebResponse<Any> {

        return doCall(verb, webRequest, context) {
            val genericType = object : GenericType<Any>() {
                override fun getType(): Type {
                    return responseType
                }

                override fun getTypeClass(): Class<*> {
                    return responseType::class.java
                }
            }
            asObject(genericType)
        }
    }

    override fun <T> call(verb: HttpVerb, webRequest: WebRequest<T>, context: RequestContext): WebResponse<String> {
        return doCall(verb, webRequest, context) {
            asString()
        }
    }

    @Suppress("ComplexMethod", "ThrowsCount")
    private fun <T, R> doCall(
        verb: HttpVerb, webRequest: WebRequest<T>, context: RequestContext,
        remoteCallFn: HttpRequest<*>.() -> HttpResponse<R>
    ): WebResponse<R> where R : Any {
        val path = baseAddress + webRequest.path
        log.trace { """Do call "$verb $path".""" }
        addSslParams()

        var request = when (verb) {
            HttpVerb.GET -> Unirest.get(path)
            HttpVerb.POST -> Unirest.post(path)
            HttpVerb.PUT -> Unirest.put(path)
            HttpVerb.DELETE -> Unirest.delete(path)
        }

        request = if(isMultipartFormRequest(webRequest)) {
            buildMultipartFormRequest(webRequest, request)
        } else {
            buildApplicationJsonRequest(webRequest, request)
        }

        context.authenticationScheme.authenticate(context.credentials, request, context)

        request = applyQueryParameters(webRequest, request)

        try {
            val response = request.remoteCallFn()
            if (!response.isSuccess || response.parsingError.isPresent) {
                val mapper = Unirest.config().objectMapper
                val errorResponseJson = mapper.writeValue(response.mapError(String::class.java))
                when (response.status) {
                    HttpStatus.BAD_REQUEST -> throw RequestErrorException(errorResponseJson)
                    HttpStatus.FORBIDDEN, HttpStatus.UNAUTHORIZED, HttpStatus.METHOD_NOT_ALLOWED, HttpStatus.PROXY_AUTHENTICATION_REQUIRED
                    -> throw PermissionException(errorResponseJson)
                    HttpStatus.NOT_FOUND -> throw MissingRequestedResourceException(errorResponseJson)
                    HttpStatus.CONFLICT -> throw ResourceAlreadyExistsException(errorResponseJson)
                    else -> {
                        throw InternalErrorException(errorResponseJson)
                    }
                }
            }

            return WebResponse(
                response.body, response.headers.all().associateBy({ it.name }, { it.value }),
                response.status, response.statusText
            )
                .also { log.trace { """Do call "$verb $path" completed.""" } }
        } catch (e: UnirestException) {
            throw InternalErrorException(e.message ?: "No message provided")
        }
    }

    private fun <T> buildMultipartFormRequest(webRequest: WebRequest<T>, request: HttpRequest<*>): HttpRequest<*> {
        var requestBuilder = request.header("accept", "multipart/form-data")
        if(request is HttpRequestWithBody) {
            requestBuilder = request.multiPartContent() as MultipartBody

            if(!webRequest.formParameters.isNullOrEmpty()) {
                webRequest.formParameters.forEach {
                    requestBuilder.field(it.key, it.value)
                }
            }

            if(!webRequest.files.isNullOrEmpty()) {
                webRequest.files.forEach { filesForParameter ->
                    addFilesForEachField(filesForParameter, requestBuilder)
                }
            }
        }
        return requestBuilder
    }

    private fun addFilesForEachField(filesForParameter: Map.Entry<String, List<RestClientFileUpload>>, requestBuilder: MultipartBody) {
        val formFieldName = filesForParameter.key
        filesForParameter.value.forEach { file ->
            // we can add multiple files to the same form field name
            requestBuilder.field(formFieldName, file.content, file.fileName)
        }
    }

    private fun <T> buildApplicationJsonRequest(webRequest: WebRequest<T>, request: HttpRequest<*>): HttpRequest<*> {
        var requestBuilder = request
        requestBuilder.header("accept", "application/json")
        requestBuilder.header("accept", "text/plain")

        if(requestBuilder is HttpRequestWithBody) {
            if (webRequest.body != null) {
                requestBuilder = requestBuilder.body(webRequest.body)
            }
        }
        return requestBuilder
    }

    private fun addSslParams() {
        log.trace { "Add Ssl params." }
        if (enableSsl) {
            val sslContext: SSLContext = SSLContexts.custom()
                .loadTrustMaterial(TrustAllStrategy())
                .build()

            val httpClient = HttpClients.custom()
                .setSSLContext(sslContext)
                .setSSLHostnameVerifier(NoopHostnameVerifier())
                .build()

            Unirest.config().let { config ->
                config.httpClient(ApacheClient.builder(httpClient).apply(config))
            }
        }
        log.trace { "Add Ssl params completed." }
    }

    private fun isMultipartFormRequest(webRequest: WebRequest<*>) =
        !webRequest.formParameters.isNullOrEmpty() || !webRequest.files.isNullOrEmpty()

    private fun <T> applyQueryParameters(webRequest: WebRequest<T>, request: HttpRequest<*>): HttpRequest<*> {
        var requestBuilder = request
        webRequest.queryParameters?.forEach { item ->
            if (item.value is Collection<*>) {
                (item.value as Collection<*>).forEach { requestBuilder = requestBuilder.queryString(item.key, it) }
            } else {
                requestBuilder = requestBuilder.queryString(item.key, item.value)
            }
        }
        return requestBuilder
    }
}
