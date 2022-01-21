package net.corda.introspiciere.server

import io.javalin.http.Handler
import io.javalin.http.HandlerType
import org.pf4j.ExtensionPoint

interface MyHandler : ExtensionPoint {
    val handlerType: HandlerType
    val path: String
    val handler: Handler
}