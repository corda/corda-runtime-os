package net.corda.e2etest.utilities

import kong.unirest.MultipartBody
import kong.unirest.Unirest
import kong.unirest.apache.ApacheClient
import org.apache.http.conn.ssl.NoopHostnameVerifier
import org.apache.http.conn.ssl.TrustAllStrategy
import org.apache.http.impl.client.HttpClients
import org.apache.http.ssl.SSLContexts
import java.net.URI
import javax.net.ssl.SSLContext

class UnirestHttpsClient(private val endpoint: URI, private val username: String, private val password: String)  :
    HttpsClient {
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

        return SimpleResponse(response.status, response.body, url)
    }

    override fun post(cmd: String, body: String): SimpleResponse {
        val url = endpoint.resolve(cmd).toURL().toString()

        val response = Unirest.post(url).basicAuth(username, password)
            .body(body)
            .asString()

        return SimpleResponse(response.status, response.body, url)
    }

    override fun put(cmd: String, body: String): SimpleResponse {
        val url = endpoint.resolve(cmd).toURL().toString()

        val response = Unirest.put(url).basicAuth(username, password)
            .body(body)
            .asString()

        return SimpleResponse(response.status, response.body, url)
    }

    override fun putMultiPart(cmd: String, fields: Map<String, String>, files: Map<String, HttpsClientFileUpload>): SimpleResponse {
        val url = endpoint.resolve(cmd).toURL().toString()

        val response = Unirest.put(url)
            .basicAuth(username, password)
            .multiPartContent()
            .fields(fields)
            .files(files)
            .asString()

        return SimpleResponse(response.status, response.body, url)
    }

    override fun get(cmd: String): SimpleResponse {
        val url = endpoint.resolve(cmd).toURL().toString()

        val response = Unirest.get(url).basicAuth(username, password).asString()
        return SimpleResponse(response.status, response.body, url)
    }

    private fun addSslParams() {
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

    private fun MultipartBody.fields(fields: Map<String, String>): MultipartBody {
        fields.entries.forEach { (name, value) -> field(name, value) }
        return this
    }

    private fun MultipartBody.files(files: Map<String, HttpsClientFileUpload>): MultipartBody {
        files.entries.forEach { (name, file) -> field(name, file.content, file.filename) }
        return this
    }
}
