package net.corda.httprpc.server.impl.internal

import io.javalin.http.util.ContextUtil
import net.corda.httprpc.HttpFileUpload
import net.corda.httprpc.server.impl.context.ClientRequestContext
import net.corda.v5.base.util.contextLogger

/**
 * Internal Context that wrap Javalin's Context.
 *
 * Exposes only a minimal set of methods for parameters retrieval. Also treats all the query and path parameters keys
 * as case-insensitive.
 */
internal class ParametersRetrieverContext(private val ctx: ClientRequestContext) {

    private companion object {
       val logger = contextLogger()
    }

    private val pathParamsMap: Map<String, String?>
    private val queryParamsMap: Map<String, List<String>>
    private val formParamsMap: Map<String, List<String>>

    init {
        // Moving all the parameters' keys to the lowercase.
        // Result will not be predictable if the same keys is used in the mixed case
        val ctxPathParamMap = ctx.pathParamMap
        pathParamsMap = ctxPathParamMap.mapKeys { it.key.lowercase() }
        if (pathParamsMap.size != ctxPathParamMap.size) {
            logger.warn("Some path parameters keys were dropped. " +
                    "Original map: $ctxPathParamMap, transformed map: $pathParamsMap")
        }
        val ctxQueryParamMap = ctx.queryParams
        queryParamsMap = ctxQueryParamMap.mapKeys { it.key.lowercase() }
        if (queryParamsMap.size != ctxQueryParamMap.size) {
            logger.warn("Some query parameters keys were dropped. " +
                    "Original map: $ctxQueryParamMap, transformed map: $queryParamsMap")
        }
        val ctxFormParamMap = ctx.formParams
        formParamsMap = ctxFormParamMap.mapKeys { it.key.lowercase() }
        if (formParamsMap.size != ctxFormParamMap.size) {
            logger.warn("Some form parameter keys were dropped. " +
                    "Original map: $ctxFormParamMap, transformed map: $formParamsMap")
        }
    }

    fun body(): String = ctx.body

    fun <T> bodyAsClass(clazz: Class<T>): T = ctx.bodyAsClass(clazz)

    fun pathParam(key: String): String {
        return ContextUtil.pathParamOrThrow(pathParamsMap, key.lowercase(), ctx.matchedPath)
    }

    fun queryParams(key: String): List<String> = queryParamsMap[key.lowercase()] ?: emptyList()

    fun queryParam(key: String, default: String? = null): String? =
        queryParamsMap[key.lowercase()]?.firstOrNull() ?: default

    fun formParams(key: String): List<String> = formParamsMap[key.lowercase()] ?: emptyList()

    fun uploadedFiles(paramName: String): List<HttpFileUpload> {
        val files = ctx.uploadedFiles(paramName)
        return files.map { file -> HttpFileUpload(file.content, file.contentType, file.extension, file.filename, file.size) }
    }

    fun <T> fromJsonString(json: String, targetClass: Class<T>): T {
        return ctx.jsonMapper.fromJsonString(json, targetClass)
    }
}
