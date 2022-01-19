package net.corda.introspiciere.server

import io.javalin.Javalin
import net.corda.introspiciere.core.HelloWorld

fun main() {
    val app = Javalin.create().start(7070)
    app.get("/helloworld") { ctx ->
        val greeting = HelloWorld().greeting()
        ctx.result(greeting)
    }
}