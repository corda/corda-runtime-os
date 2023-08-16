package net.corda.applications.workers.workercommon

import io.javalin.Javalin
import org.osgi.service.component.annotations.Component

@Component(service = [JavalinServer::class])
class JavalinServer {
    companion object {
        private var JAVALIN_INSTANCE: Javalin? = null;
    }
    fun getServer(): Javalin? {
        return JavalinServer.JAVALIN_INSTANCE
    }
}