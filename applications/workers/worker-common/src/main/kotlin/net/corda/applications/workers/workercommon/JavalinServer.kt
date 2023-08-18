package net.corda.applications.workers.workercommon

import io.javalin.Javalin
import org.osgi.service.component.annotations.Component

@Component(service = [JavalinServer::class])
class JavalinServer {
    private val javalinServer: Javalin? = null
    fun getServer(): Javalin? {
        return javalinServer
    }
}