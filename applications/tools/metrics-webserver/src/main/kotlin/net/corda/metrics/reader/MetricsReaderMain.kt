package net.corda.metrics.reader

import io.javalin.Javalin
import io.javalin.core.util.Header
import io.javalin.http.HandlerType
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.CountDownLatch

class MetricsReaderMain {

    private val shutdownListener = CountDownLatch(1)
    private var server: Javalin? = null

    fun start(metricsFile: String, port: Int) {
        try {
            val measurements = Files.readString(Paths.get(metricsFile))
            server = Javalin.create()

            server?.addHandler(HandlerType.GET, "/metrics") { context ->
                context.result(measurements)
                context.header(Header.CACHE_CONTROL, "no-cache")
            }

            server?.start(port)
        } catch (e: Exception) {
            //TOD0: add logging
            println(e)
            close()
        }
        shutdownListener.await()
    }

    fun close() {
        server?.stop()
        shutdownListener.countDown()
    }
}