package net.corda.web.server

import io.javalin.Javalin
import org.osgi.service.component.annotations.Component

@Component(service = [JavalinFactory::class])
class JavalinFactory {
    fun create(): Javalin = Javalin.create()

}