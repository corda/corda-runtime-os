package net.corda.introspiciere.server

import io.javalin.http.Handler

internal class HelloWorldController {
    fun greeting(): Handler = Handler { ctx ->
        ctx.result("Hello world!!")
    }
}