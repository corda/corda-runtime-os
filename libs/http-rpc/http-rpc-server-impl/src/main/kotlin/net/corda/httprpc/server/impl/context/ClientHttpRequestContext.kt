package net.corda.httprpc.server.impl.context

import io.javalin.http.Context
import io.javalin.http.UploadedFile
import io.javalin.plugin.json.JsonMapper
import io.javalin.plugin.json.jsonMapper

/**
 * Implementation of [ClientRequestContext] which implements functionality using [Context].
 */
internal class ClientHttpRequestContext(private val ctx: Context) : ClientRequestContext {

    override val pathParamMap: Map<String, String>
        get() = ctx.pathParamMap()

    override val queryParams: Map<String, List<String>>
        get() = ctx.queryParamMap()

    override val matchedPath: String
        get() = ctx.matchedPath()

    override val formParams: Map<String, List<String>>
        get() = ctx.formParamMap()

    override val body: String
        get() = ctx.body()

    override val jsonMapper: JsonMapper
        get() = ctx.jsonMapper()

    override fun <T> bodyAsClass(clazz: Class<T>): T = ctx.bodyAsClass(clazz)

    override fun uploadedFiles(fileName: String): List<UploadedFile> = ctx.uploadedFiles(fileName)
}