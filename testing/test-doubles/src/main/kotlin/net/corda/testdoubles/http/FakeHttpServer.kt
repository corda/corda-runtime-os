package net.corda.testdoubles.http

import io.javalin.Javalin
import io.javalin.http.Context

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

    fun addGetHandler(path: String, action: HandlerContext.(Request) -> Unit) {
        app.get(path) {
            val handlerContext = HandlerContextImpl(it)
            val request = Request(path, it.queryParamMap())
            action(handlerContext, request)
        }
    }
}

class Request(val path: String, val queryParams: Map<String, List<String>>) {
    operator fun component1(): String = path
    operator fun component2(): Map<String, List<String>> = queryParams
}

interface HandlerContext {
    fun json(any: Any)
}

internal class HandlerContextImpl(private val ctx: Context) : HandlerContext {
    override fun json(any: Any) {
        ctx.json(any)
    }
}