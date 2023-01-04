package net.corda.httprpc.server.impl.context

import io.javalin.core.security.BasicAuthCredentials
import io.javalin.core.util.Header
import io.javalin.http.UploadedFile
import io.javalin.http.util.ContextUtil
import io.javalin.plugin.json.JsonMapper
import net.corda.httprpc.server.impl.security.HttpRpcSecurityManager

/**
 * Abstract HTTP request or WebSocket request
 */
interface ClientRequestContext {

    companion object {
        const val METHOD_SEPARATOR = ":"
    }

    /**
     * Gets the request method.
     */
    val method: String

    /**
     * Gets a request header by name, or null.
     */
    fun header(header: String): String?

    /**
     * Gets a map of all the keys and values.
     */
    val pathParamMap: Map<String, String>

    /**
     * Gets a map with all the query param keys and values.
     */
    val queryParams: Map<String, List<String>>

    /**
     * Gets the request query string, or null.
     */
    val queryString: String?

    /**
     * Gets the path that was used to match request (also includes before/after paths)
     */
    val matchedPath: String

    /**
     * Gets the request path.
     */
    val path: String

    /**
     * Gets a map with all the form param keys and values.
     */
    val formParams: Map<String, List<String>> get() = emptyMap()

    /**
     * Gets the request body as a [String].
     */
    val body: String get() = throw UnsupportedOperationException()

    /**
     * Gets applicable JSON mapper
     */
    val jsonMapper: JsonMapper get() = throw UnsupportedOperationException()

    /**
     * Maps a JSON body to a Java/Kotlin class using the registered [io.javalin.plugin.json.JsonMapper]
     */
    fun <T> bodyAsClass(clazz: Class<T>): T = throw UnsupportedOperationException()

    /**
     * Gets a list of [UploadedFile]s for the specified name, or empty list.
     */
    fun uploadedFiles(fileName: String): List<UploadedFile> = throw UnsupportedOperationException()

    /**
     * Add special header values to the response to indicate which authentication methods are supported.
     * More information can be found [here](https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/WWW-Authenticate).
     */
    fun addWwwAuthenticateHeaders(securityManager: HttpRpcSecurityManager) {}

    fun getResourceAccessString(): String {
        // Examples of strings will look like:
        // GET:/api/v1/permission/getpermission?id=c048679a-9654-4359-befc-9d2d22695a43
        // POST:/api/v1/user/createuser
        return method + METHOD_SEPARATOR + path.trimEnd('/') + if (!queryString.isNullOrBlank()) "?$queryString" else ""
    }

    /**
     * Checks whether basic-auth credentials from the request exists.
     *
     * Returns a Boolean which is true if there is an Authorization header with
     * Basic auth credentials. Returns false otherwise.
     */
    fun basicAuthCredentialsExist(): Boolean = ContextUtil.hasBasicAuthCredentials(header(Header.AUTHORIZATION))

    /**
     * Gets basic-auth credentials from the request, or throws.
     *
     * Returns a wrapper object [BasicAuthCredentials] which contains the
     * Base64 decoded username and password from the Authorization header.
     */
    fun basicAuthCredentials(): BasicAuthCredentials = ContextUtil.getBasicAuthCredentials(header(Header.AUTHORIZATION))
}