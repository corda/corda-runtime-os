package net.corda.metrics.reader

import io.javalin.Javalin
import io.javalin.core.util.Header
import io.javalin.http.HandlerType
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.CountDownLatch

class MetricsReaderMain {

    private val shutdownListener = CountDownLatch(1)
    private var server: Javalin? = null

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java)
    }

    fun start(metricsFile: String, port: Int) {
        logger.info("Starting metrics reader.")
        try {
            val measurements = Files.readString(Paths.get(metricsFile))
            server = Javalin.create()

            server?.addHandler(HandlerType.GET, "/metrics") { context ->
                context.result(measurements)
                context.header(Header.CACHE_CONTROL, "no-cache")
            }

            server?.start(port)
        } catch (e: Exception) {
            logger.error("Something went wrong.", e)
            close()
        }
        shutdownListener.await()
    }

    fun close() {
        logger.info("Shutting down metrics reader.")
        server?.stop()
        shutdownListener.countDown()
    }
}