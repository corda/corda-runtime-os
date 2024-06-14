package net.corda.rest.server.impl.context

import io.javalin.http.Context
import io.javalin.http.Header
import io.javalin.http.UploadedFile
import io.javalin.json.JsonMapper
import net.corda.data.rest.PasswordExpiryStatus
import net.corda.rest.server.impl.security.RestAuthenticationProvider

/**
 * Implementation of [ClientRequestContext] which implements functionality using [Context].
 */
internal class ClientHttpRequestContext(private val ctx: Context) : ClientRequestContext {

    override val method: String
        get() = ctx.method().name

    override fun header(header: String): String? = ctx.header(header)

    override val pathParamMap: Map<String, String>
        get() = ctx.pathParamMap()

    override val queryParams: Map<String, List<String>>
        get() = ctx.queryParamMap()

    override val queryString: String?
        get() = ctx.queryString()

    override val matchedPath: String
        get() = ctx.matchedPath()

    override val path: String
        get() = ctx.path()

    override val body: String
        get() = ctx.body()

    override val jsonMapper: JsonMapper
        get() = throw UnsupportedOperationException()

    override fun <T> bodyAsClass(clazz: Class<T>): T = ctx.bodyAsClass(clazz)

    override fun formParamMap(): Map<String, List<String>> = ctx.formParamMap()

    override fun uploadedFiles(fileName: String): List<UploadedFile> = ctx.uploadedFiles(fileName)

    override fun addWwwAuthenticateHeaders(restAuthProvider: RestAuthenticationProvider) {
        val authMethods = restAuthProvider.getSchemeProviders().map {
            val parameters = it.provideParameters()
            val attributes = if (parameters.isEmpty()) {
                ""
            } else {
                parameters.map { (k, v) -> "$k=\"$v\"" }.joinToString(", ")
            }
            "${it.authenticationMethod} $attributes"
        }

        addHeaderValues(authMethods)
    }

    override fun addPasswordExpiryHeader(expiryStatus: PasswordExpiryStatus) {
        ctx.res().addHeader(Header.WARNING, "199 - PasswordExpiryStatus is $expiryStatus")
    }

    private fun addHeaderValues(values: Iterable<String>) {
        values.forEach {
            ctx.res().addHeader(Header.WWW_AUTHENTICATE, it)
        }
    }
}
