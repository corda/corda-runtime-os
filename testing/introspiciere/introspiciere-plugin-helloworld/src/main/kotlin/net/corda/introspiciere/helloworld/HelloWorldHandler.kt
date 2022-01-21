package net.corda.introspiciere.helloworld

import io.javalin.http.Handler
import io.javalin.http.HandlerType
import net.corda.introspiciere.core.HelloWorld
import net.corda.introspiciere.server.MyHandler
import org.pf4j.Extension
import org.pf4j.Plugin
import org.pf4j.PluginWrapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class HelloworldPlugin(wrapper: PluginWrapper) : Plugin(wrapper) {
    companion object {
        private val logger: Logger = LoggerFactory.getLogger(HelloworldPlugin::class.java)
    }

    override fun start() {
        logger.info("HelloPlugin.start()")
    }

    override fun stop() {
        logger.info("HelloPlugin.stop()")
    }

    @Extension
    class HelloWorldHandler : MyHandler {
        override val handlerType: HandlerType = HandlerType.GET

        override val path: String = "/helloworld"

        override val handler: Handler = Handler { ctx ->
            val greeting = HelloWorld().greeting()
            ctx.result(greeting)
        }
    }
}
