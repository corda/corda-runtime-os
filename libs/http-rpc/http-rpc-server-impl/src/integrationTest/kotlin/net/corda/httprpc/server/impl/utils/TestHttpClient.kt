package net.corda.httprpc.server.impl.utils

import io.javalin.core.util.Header.ORIGIN
import java.io.InputStream
import kong.unirest.HttpRequest
import kong.unirest.HttpRequestWithBody
import kong.unirest.Unirest
import kong.unirest.apache.ApacheClient
import net.corda.httprpc.tools.HttpVerb
import org.apache.http.conn.ssl.NoopHostnameVerifier
import org.apache.http.conn.ssl.TrustAllStrategy
import org.apache.http.impl.client.HttpClients
import org.apache.http.ssl.SSLContexts
import javax.net.ssl.SSLContext
import kong.unirest.MultipartBody

data class TestClientFileUpload(val fileContent: InputStream, val fileName: String)

data class WebRequest<T>(
    val path: String,
    val body: T? = null,
    val queryParameters: Map<String, Any>? = null,
    val formParameters: Map<String, Any>? = null
)

data class WebResponse<T>(val body: T?, val headers: Map<String, String>, val responseStatus: Int, val responseStatusText: String?)

interface TestHttpClient {
    fun <T, R> call(verb: HttpVerb, webRequest: WebRequest<T>, responseClass: Class<R>, userName: String = "", password: String = ""):
            WebResponse<R> where R : Any

    fun <T> call(verb: HttpVerb, webRequest: WebRequest<T>, userName: String = "", password: String = ""): WebResponse<String>
    fun <T> call(verb: HttpVerb, webRequest: WebRequest<T>, bearerToken: String): WebResponse<String>
    val baseAddress: String
}

class TestHttpClientUnirestImpl(override val baseAddress: String, private val enableSsl: Boolean = false) : TestHttpClient {

    override fun <T, R> call(verb: HttpVerb, webRequest: WebRequest<T>, responseClass: Class<R>, userName: String, password: String):
            WebResponse<R> where R : Any {

        addSslParams()

        var request = when (verb) {
            HttpVerb.GET -> Unirest.get(baseAddress + webRequest.path).basicAuth(userName, password)
            HttpVerb.POST -> Unirest.post(baseAddress + webRequest.path).basicAuth(userName, password)
            HttpVerb.PUT -> Unirest.put(baseAddress + webRequest.path).basicAuth(userName, password)
            HttpVerb.DELETE -> Unirest.delete(baseAddress + webRequest.path).basicAuth(userName, password)
        }.addOriginHeader()

        if(request is HttpRequestWithBody) {
            applyBody(webRequest, request)
        }

        applyQueryParameters(webRequest, request)

        val response = request.asObject(responseClass)
        return WebResponse(
            response.body, response.headers.all()
                .associateBy({ it.name }, { it.value }), response.status, response.statusText
        )
    }

    private fun HttpRequest<*>.addOriginHeader() = header(ORIGIN, "localhost")

    override fun <T> call(verb: HttpVerb, webRequest: WebRequest<T>, userName: String, password: String): WebResponse<String> {
        return doCall(verb, webRequest) {
            if (userName.isNotEmpty() || password.isNotEmpty()) {
                basicAuth(userName, password)
            }
        }
    }

    private fun <T> doCall(verb: HttpVerb, webRequest: WebRequest<T>, encodeAuth: HttpRequest<*>.() -> Unit): WebResponse<String> {

        addSslParams()

        val path = baseAddress + webRequest.path
        var request: HttpRequest<*> = when (verb) {
            HttpVerb.GET -> Unirest.get(path)
            HttpVerb.POST -> Unirest.post(path)
            HttpVerb.PUT -> Unirest.put(path)
            HttpVerb.DELETE -> Unirest.delete(path)
        }.addOriginHeader()

        request.encodeAuth()

        if(request is HttpRequestWithBody) {
            request = applyBody(webRequest, request)
        }
        request = applyQueryParameters(webRequest, request)

        val response = request.asString()
        return WebResponse(
            response.body, response.headers.all()
                .associateBy({ it.name }, { it.value }), response.status, response.statusText
        )
    }

    private fun <T> applyBody(webRequest: WebRequest<T>, request: HttpRequestWithBody): HttpRequest<*> {
        var requestBuilder: HttpRequest<*> = request
        if (!webRequest.formParameters.isNullOrEmpty()) {
            requestBuilder = applyFormParameters(request, webRequest.formParameters)
        } else if (webRequest.body != null) {
            requestBuilder = request.body(webRequest.body)
        }
        return requestBuilder
    }

    private fun applyFormParameters(request: HttpRequestWithBody, formParameters: Map<String, Any>): MultipartBody {
        val multiPartContent = request.multiPartContent()
        val formParamNames = formParameters.keys
        for (paramName in formParamNames) {
            val paramValue = formParameters[paramName]
            // this doesn't support lists of form parameters
            if (paramValue is TestClientFileUpload) {
                multiPartContent.field(paramName, paramValue.fileContent, paramValue.fileName)
            } else if (paramValue is String) {
                multiPartContent.field(paramName, paramValue)
            }
        }
        return multiPartContent
    }

    private fun <T> applyQueryParameters(webRequest: WebRequest<T>, request: HttpRequest<*>): HttpRequest<*> {
        webRequest.queryParameters?.forEach { item ->
            if (item.value is Collection<*>) {
                (item.value as Collection<*>).forEach { request.queryString(item.key, it) }
            } else {
                request.queryString(item.key, item.value)
            }
        }
        return request
    }

    override fun <T> call(verb: HttpVerb, webRequest: WebRequest<T>, bearerToken: String): WebResponse<String> {
        return doCall(verb, webRequest) {
            header("Authorization", "Bearer $bearerToken")
        }
    }

    private fun addSslParams() {
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
    }
}
