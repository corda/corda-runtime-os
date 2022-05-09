package net.corda.httprpc.client.processing

import java.io.InputStream
import java.lang.reflect.Method
import java.net.URLEncoder
import net.corda.httprpc.HttpFileUpload

internal data class WebRequest<T>(
    val path: String,
    val body: T? = null,
    val queryParameters: Map<String, Any?>? = null,
    val formParameters: Map<String, String>? = null,
    val files: Map<String, HttpRpcClientFileUpload>? = null
)
internal data class WebResponse<T>(val body: T?, val headers: Map<String, String>, val responseStatus: Int, val responseStatusText: String?)

@Suppress("ComplexMethod")
internal fun Method.parametersFrom(args: Array<out Any?>?, extraBodyParameters: Map<String, Any?> = emptyMap()): ResolvedParameters {
    if(this.isMultipartFormRequest()) {
        return ResolvedParameters(
            null,
            emptyMap(),
            emptyMap(),
            args?.let { this.formParametersFrom(it) } ?: emptyMap(),
            args?.let { this.filesFrom(it) } ?: emptyMap(),
        )
    }

    return ResolvedParameters(
        args?.let { this.bodyParametersFrom(args, extraBodyParameters) },
        args?.let { this.pathParametersFrom(it) } ?: emptyMap(),
        args?.let { this.queryParametersFrom(it) } ?: emptyMap(),
        emptyMap(),
        emptyMap()
    )
}

internal fun Method.isMultipartFormRequest() : Boolean {
    return this.parameters.any { it.type == InputStream::class.java || it.type == HttpFileUpload::class.java }
}

internal fun ResolvedParameters.toWebRequest(rawPath: String) = WebRequest<Any>(
    rawPath.replacePathParameters(pathParams).replace("/+".toRegex(), "/"),
    body = body,
    queryParameters = queryParams,
    formParameters = formParams,
    files = files
)

internal fun String.encodeParam(): String = URLEncoder.encode(this, "UTF-8")

internal data class ResolvedParameters(
    val body: String?,
    val pathParams: Map<String, String>,
    val queryParams: Map<String, Any?>,
    val formParams: Map<String, String>,
    val files: Map<String, HttpRpcClientFileUpload>
)

internal data class HttpRpcClientFileUpload(
    val content: InputStream,
    val fileName: String
)