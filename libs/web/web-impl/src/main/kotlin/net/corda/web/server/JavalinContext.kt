package net.corda.web.server

import io.javalin.http.Context
import java.io.InputStream
import net.corda.rest.ResponseCode
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.web.api.WebContext

internal class JavalinContext(private val ctx: Context) : WebContext {
    override fun status(status: ResponseCode) {
        ctx.status(status.statusCode)
    }

    override fun bodyAsBytes() = ctx.bodyAsBytes()

    override fun body() = ctx.body()

    /**
     * Result we need to cast the result to one of the Javalin supported types
     */
    override fun result(result: Any) {
        when (result) {
            is String -> ctx.result(result)
            is ByteArray -> ctx.result(result)
            is InputStream -> ctx.result(result)
            else -> throw CordaRuntimeException("Result of web handle must be String, ByteArray, or InputStream")
        }
    }

    override fun header(header: String, value: String) {
        ctx.header(header, value)
    }

    override fun header(header: String) {
        ctx.header(header)
    }

}