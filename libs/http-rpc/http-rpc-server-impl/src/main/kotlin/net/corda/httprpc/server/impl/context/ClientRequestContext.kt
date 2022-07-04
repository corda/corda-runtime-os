package net.corda.httprpc.server.impl.context

import io.javalin.http.UploadedFile
import io.javalin.plugin.json.JsonMapper

/**
 * Abstract HTTP request or WebSocket request
 */
interface ClientRequestContext {

    /**
     * Gets a map of all the keys and values.
     */
    val pathParamMap: Map<String, String>

    /**
     * Gets a map with all the query param keys and values.
     */
    val queryParams: Map<String, List<String>>

    /**
     * Gets the path that was used to match request (also includes before/after paths)
     */
    val matchedPath: String

    /**
     * Gets a map with all the form param keys and values.
     */
    val formParams: Map<String, List<String>> get() = throw UnsupportedOperationException()

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
}