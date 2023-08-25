package net.corda.web.server

import io.javalin.http.Context
import java.io.InputStream
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.web.api.WebContext

class JavalinContext(private val ctx: Context) : WebContext {
    override fun status(status: Int) {
        ctx.status(status)
    }

    override fun bodyAsBytes() = ctx.bodyAsBytes()

    override fun body() = ctx.body()

    override fun result(result: Any) {
        when (result) {
            is String -> ctx.result(result)
            is ByteArray -> ctx.result(result)
            is InputStream -> ctx.result(result)
            else -> throw CordaRuntimeException("result of web handle must be String, ByteArray, or InputStream")
        }
    }

    override fun header(header: String, value: String) {
        ctx.header(header, value)
    }

    override fun header(header: String) {
        ctx.header(header)
    }

}