package net.corda.httprpc.client.internal.processing

import java.lang.reflect.Method
import java.net.URLEncoder

data class WebRequest<T>(val path: String, val body: T? = null, val queryParameters: Map<String, Any?>? = null)
data class WebResponse<T>(val body: T?, val headers: Map<String, String>, val responseStatus: Int, val responseStatusText: String?)

fun Method.parametersFrom(args: Array<out Any?>?, extraBodyParameters: Map<String, Any?> = emptyMap()): ResolvedParameters =
        ResolvedParameters(
                args?.let { this.bodyParametersFrom(args, extraBodyParameters) },
                args?.let { this.pathParametersFrom(it) } ?: emptyMap(),
                args?.let { this.queryParametersFrom(it) } ?: emptyMap()
        )

fun ResolvedParameters.toWebRequest(rawPath: String) = WebRequest<Any>(
        rawPath.replacePathParameters(pathParams).replace("/+".toRegex(), "/"),
        body = body,
        queryParameters = queryParams
)

fun String.encodeParam(): String = URLEncoder.encode(this, "UTF-8")

data class ResolvedParameters(val body: String?, val pathParams: Map<String, String>, val queryParams: Map<String, Any?>)