package net.corda.rest.server.impl.internal

import net.corda.rest.HttpFileUpload
import net.corda.rest.server.impl.context.ClientRequestContext
import org.slf4j.LoggerFactory

/**
 * Internal Context that wrap Javalin's Context.
 *
 * Exposes only a minimal set of methods for parameters retrieval. Also treats all the query and path parameters keys
 * as case-insensitive.
 */
internal class ParametersRetrieverContext(private val ctx: ClientRequestContext) {

    private companion object {
        val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val pathParamsMap: Map<String, String?>
    private val queryParamsMap: Map<String, List<String>>

    init {
        // Moving all the parameters' keys to the lowercase.
        // Result will not be predictable if the same keys is used in the mixed case
        val ctxPathParamMap = ctx.pathParamMap
        pathParamsMap = ctxPathParamMap.mapKeys { it.key.lowercase() }
        if (pathParamsMap.size != ctxPathParamMap.size) {
            logger.warn(
                "Some path parameters keys were dropped. " +
                    "Original map: $ctxPathParamMap, transformed map: $pathParamsMap"
            )
        }
        val ctxQueryParamMap = ctx.queryParams
        queryParamsMap = ctxQueryParamMap.mapKeys { it.key.lowercase() }
        if (queryParamsMap.size != ctxQueryParamMap.size) {
            logger.warn(
                "Some query parameters keys were dropped. " +
                    "Original map: $ctxQueryParamMap, transformed map: $queryParamsMap"
            )
        }
    }

    fun body(): String = ctx.body

    fun <T> bodyAsClass(clazz: Class<T>): T = ctx.bodyAsClass(clazz)

    fun formParamMap(): Map<String, List<String>> = ctx.formParamMap()

    fun pathParam(key: String): String {
        return pathParamsMap[key.lowercase()]
            ?: throw IllegalArgumentException("Path parameter '$key' not found in path '${ctx.matchedPath}'")
    }

    fun queryParams(key: String): List<String> = queryParamsMap[key.lowercase()] ?: emptyList()

    fun queryParam(key: String, default: String? = null): String? =
        queryParamsMap[key.lowercase()]?.firstOrNull() ?: default

    fun uploadedFiles(paramName: String): List<HttpFileUpload> {
        val files = ctx.uploadedFiles(paramName)
        return files.map { file ->
            HttpFileUpload(
                file.content(),
                file.contentType(),
                file.extension(),
                file.filename(),
                file.size()
            )
        }
    }

    fun <T> fromJsonString(json: String, targetClass: Class<T>): T? {
        // The method fromJsonString does not accept null as a return value
        // because the type accepted by the method must be a subclass of Any.
        // In order for the function to be able to return null, the type should be a
        // subclass of Any?.
        // An attempt to return null results in a null exception.
        if (json == "null") {
            return null
        }
        return ctx.jsonMapper.fromJsonString(json, targetClass)
    }
}
