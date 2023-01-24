package net.corda.httprpc.client.processing

import java.io.InputStream
import java.lang.reflect.Method
import java.lang.reflect.Parameter
import java.lang.reflect.ParameterizedType
import java.net.URLEncoder
import net.corda.httprpc.HttpFileUpload

internal data class WebRequest<T>(
    val path: String,
    val body: T? = null,
    val queryParameters: Map<String, Any?>? = null,
    val formParameters: Map<String, String>? = null,
    val files: Map<String, List<RestClientFileUpload>>? = null
)
internal data class WebResponse<T>(val body: T?, val headers: Map<String, String>, val responseStatus: Int, val responseStatusText: String?)

@Suppress("ComplexMethod")
internal fun Method.parametersFrom(args: Array<out Any?>?, extraBodyParameters: Map<String, Any?> = emptyMap()): ResolvedParameters {
    if(this.isMultipartFormRequest()) {
        return ResolvedParameters(
            null, // multipart form requests have all fields as form / query / path parameters or file uploads.
            args?.let { this.pathParametersFrom(it) } ?: emptyMap(),
            args?.let { this.queryParametersFrom(it) } ?: emptyMap(),
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

internal fun Method.isMultipartFormRequest() =
    this.parameters.any { isParameterAFile(it) || isParameterAListOfFiles(it) }

private fun isParameterAFile(it: Parameter) =
    it.type == InputStream::class.java || it.type == HttpFileUpload::class.java

internal fun isParameterAListOfFiles(it: Parameter) =
    (it.parameterizedType is ParameterizedType && Collection::class.java.isAssignableFrom(it.type)
            && (it.parameterizedType as ParameterizedType).actualTypeArguments.size == 1
            && (it.parameterizedType as ParameterizedType).actualTypeArguments.first() == HttpFileUpload::class.java)

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
    val files: Map<String, List<RestClientFileUpload>>
)

internal data class RestClientFileUpload(
    val content: InputStream,
    val fileName: String
)