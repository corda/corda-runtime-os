package net.corda.testdoubles.http

import io.javalin.Javalin
import io.javalin.http.Context
import io.javalin.http.HandlerType

class FakeHttpServer(private val port: Int = 0) {
    private lateinit var app: Javalin

    val endpoint: String
        get() = "http://localhost:${app.port()}"

    fun start() {
        app = Javalin.create()
        app.start(port)
    }

    fun stop() {
        app.stop()
    }

    fun handle(method: String, path: String, action: HandlerContext.() -> Unit) {
        app.addHandler(HandlerType.valueOf(method.toUpperCase()), path) {
            val handlerContext = HandlerContextImpl(it)
//            val request = Request(path, it.queryParamMap(), it.body())
            action(handlerContext)
        }
    }
}

//class Request(val path: String, val queryParams: Map<String, List<String>>, val body: String) {
//    operator fun component1(): String = path
//    operator fun component2(): Map<String, List<String>> = queryParams
//    operator fun component3(): String = body
//}

interface HandlerContext {
    fun path(): String
    fun queryParam(name: String): String?
    fun queryParams(name: String): List<String>
    fun <T> bodyAs(clss: Class<T>): T
    fun json(any: Any)
}

inline fun <reified T> HandlerContext.bodyAs(): T = bodyAs(T::class.java)

internal class HandlerContextImpl(private val ctx: Context) : HandlerContext {

    override fun path(): String = ctx.path()

    override fun queryParam(name: String): String? = ctx.queryParam(name)

    override fun queryParams(name: String): List<String> = ctx.queryParams(name)

    override fun <T> bodyAs(clss: Class<T>): T = ctx.bodyAsClass(clss)

    override fun json(any: Any) {
        ctx.json(any)
    }
}