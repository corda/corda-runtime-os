package net.corda.applications.workers.workercommon

import io.javalin.Javalin
import org.osgi.service.component.annotations.Component

@Component(service = [WorkerWebServer::class])
class JavalinServer: WorkerWebServer<Javalin?> {

    private val javalinServer: Javalin? = null
    override fun getServer(): Javalin? {
        return javalinServer
    }
}