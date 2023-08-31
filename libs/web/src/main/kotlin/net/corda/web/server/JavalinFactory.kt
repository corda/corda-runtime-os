package net.corda.web.server

import io.javalin.Javalin
import net.corda.tracing.configureJavalinForTracing
import org.osgi.service.component.annotations.Component

@Component(service = [JavalinFactory::class])
class JavalinFactory {
    fun create(): Javalin = Javalin.create{
        configureJavalinForTracing(it)
    }
}