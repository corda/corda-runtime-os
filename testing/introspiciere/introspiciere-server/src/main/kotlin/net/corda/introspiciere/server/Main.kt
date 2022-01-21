package net.corda.introspiciere.server

import io.javalin.Javalin
import org.pf4j.*
import java.io.File
import java.nio.file.Path


fun main() {
    val pluginsDir = Path.of("testing/introspiciere/introspiciere-server/plugins")
    val pluginManager = DefaultPluginManager(pluginsDir)

    pluginManager.loadPlugins()
    pluginManager.startPlugins()

    val handlers = pluginManager.getExtensions(MyHandler::class.java)
    println(File("").absoluteFile)
    println("Handlers found: ${handlers.size}")

    val app = Javalin.create().start(7070)

    handlers.forEach {
        app.addHandler(it.handlerType, it.path, it.handler)
    }
}
