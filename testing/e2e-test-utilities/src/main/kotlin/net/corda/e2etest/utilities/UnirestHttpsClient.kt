package net.corda.e2etest.utilities

import kong.unirest.Config
import kong.unirest.Headers
import kong.unirest.HttpRequestSummary
import kong.unirest.HttpResponse
import kong.unirest.Interceptor
import kong.unirest.MultipartBody
import kong.unirest.Unirest
import kong.unirest.apache.ApacheClient
import net.corda.tracing.addTraceContextToHttpRequest
import org.apache.http.client.config.RequestConfig
import org.apache.http.conn.ssl.NoopHostnameVerifier
import org.apache.http.conn.ssl.TrustAllStrategy
import org.apache.http.impl.client.HttpClients
import org.apache.http.ssl.SSLContexts
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.Exception
import java.net.URI
import java.net.http.HttpRequest
import javax.net.ssl.SSLContext

class UnirestHttpsClient(private val endpoint: URI, private val username: String, private val password: String)  :
    HttpsClient {

    private companion object {
        val logger: Logger = LoggerFactory.getLogger(UnirestHttpsClient::class.java)

        fun Headers.toInternal(): List<Pair<String, String>> {
            return all().map { it.name to it.value }
        }
    }

    init {
        addSslParams()
    }

    override fun postMultiPart(cmd: String, fields: Map<String, String>, files: Map<String, HttpsClientFileUpload>): SimpleResponse {
        val url = endpoint.resolve(cmd).toURL().toString()

        val response = Unirest.post(url)
            .basicAuth(username, password)
            .multiPartContent()
            .fields(fields)
            .files(files)
            .asString()

        return SimpleResponse(response.status, response.body, url, response.headers.toInternal())
    }

    override fun post(cmd: String, body: String): SimpleResponse {
        val url = endpoint.resolve(cmd).toURL().toString()
        val response = Unirest.post(url)
            .basicAuth(username, password)
            .body(body)
            .headers(obtainTracingHeaders())
            .asString()

        return SimpleResponse(response.status, response.body, url, response.headers.toInternal())
    }

    private fun obtainTracingHeaders(): Map<String, String> {
        // Capture headers into Java standard HTTP request builder to later
        // use it with Unirest
        val builder = HttpRequest.newBuilder(URI.create("http://localhost:1234"))
        addTraceContextToHttpRequest(builder)
        val headers = with(builder.build()) {
            // Each key might have a list of values, whereas UniRest does not support that
            headers().map().map { entry -> entry.key to entry.value.first() }.toMap()
        }
        return headers
    }

    override fun put(cmd: String, body: String): SimpleResponse {
        val url = endpoint.resolve(cmd).toURL().toString()

        val response = Unirest.put(url).basicAuth(username, password)
            .body(body)
            .asString()

        return SimpleResponse(response.status, response.body, url, response.headers.toInternal())
    }

    override fun putMultiPart(cmd: String, fields: Map<String, String>, files: Map<String, HttpsClientFileUpload>): SimpleResponse {
        val url = endpoint.resolve(cmd).toURL().toString()

        val response = Unirest.put(url)
            .basicAuth(username, password)
            .multiPartContent()
            .fields(fields)
            .files(files)
            .asString()

        return SimpleResponse(response.status, response.body, url, response.headers.toInternal())
    }

    override fun get(cmd: String): SimpleResponse {
        val url = endpoint.resolve(cmd).toURL().toString()

        val response = Unirest.get(url).basicAuth(username, password).asString()
        return SimpleResponse(response.status, response.body, url, response.headers.toInternal())
    }

    override fun delete(cmd: String): SimpleResponse {
        val url = endpoint.resolve(cmd).toURL().toString()

        val response = Unirest.delete(url).basicAuth(username, password).asString()
        return SimpleResponse(response.status, response.body, url, response.headers.toInternal())
    }

    private fun addSslParams() {
        val sslContext: SSLContext = SSLContexts.custom()
            .loadTrustMaterial(TrustAllStrategy())
            .build()

        val requestConfig = RequestConfig.custom()
            .setConnectionRequestTimeout(60_000)
            .setConnectTimeout(60_000)
            .setSocketTimeout(60_000)
            .build()

        val httpClient = HttpClients.custom()
            .setSSLContext(sslContext)
            .setSSLHostnameVerifier(NoopHostnameVerifier())
            .setDefaultRequestConfig(requestConfig)
            .setMaxConnTotal(1)
            .build()

        Unirest.config()
            .let { config ->
                val client = ApacheClient.builder(httpClient).apply(config) as ApacheClient
                config.interceptor(FailureReportingInterceptor(client, logger))
                config.httpClient(client)
            }
    }

    private fun MultipartBody.fields(fields: Map<String, String>): MultipartBody {
        fields.entries.forEach { (name, value) -> field(name, value) }
        return this
    }

    private fun MultipartBody.files(files: Map<String, HttpsClientFileUpload>): MultipartBody {
        files.entries.forEach { (name, file) -> field(name, file.content, file.filename) }
        return this
    }
}

private class FailureReportingInterceptor(private val client: ApacheClient, private val logger: Logger) : Interceptor {
    override fun onFail(e: Exception?, request: HttpRequestSummary?, config: Config?): HttpResponse<*> {

        val connectionManager = client.manager
        val routesStats = connectionManager.routes.joinToString("\n") {
            route -> "Route: $route => ${connectionManager.getStats(route)}"
        }

        logger.error(
            "Failed to process HTTP request ($request). Total pool stats: ${connectionManager.totalStats}. " +
                    "Routes stats: $routesStats.", e
        )

        return super.onFail(e, request, config)
    }
}
