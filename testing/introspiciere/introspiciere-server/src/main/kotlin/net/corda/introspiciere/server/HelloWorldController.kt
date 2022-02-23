package net.corda.introspiciere.server

import io.javalin.http.Handler
import net.corda.introspiciere.core.HelloWorldUseCase

internal class HelloWorldController {
    fun greeting(): Handler = Handler { ctx ->
        wrapException {
            HelloWorldUseCase { ctx.result(it) }
        }
    }
}