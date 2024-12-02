package net.corda.metrics.reader

import io.javalin.Javalin
import io.javalin.http.HandlerType
import io.javalin.http.Header
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.CountDownLatch

class MetricsReaderMain {

    private val shutdownListener = CountDownLatch(1)
    private var server: Javalin? = null

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java)
    }

    private var pos = 0
    private val readings = mutableListOf<String>()

    fun start(metricsFile: String, port: Int) {
        logger.info("Starting metrics reader.")
        try {
            loadMeasurements(Paths.get(metricsFile))
            server = Javalin.create()

            server?.addHttpHandler(HandlerType.GET, "/metrics") { context ->
                context.result(nextReading())
                context.header(Header.CACHE_CONTROL, "no-cache")
            }

            server?.start(port)
        } catch (e: Exception) {
            logger.error("Something went wrong.", e)
            close()
        }
        shutdownListener.await()
    }


    private fun loadMeasurements(path: Path) {
        Files.newBufferedReader(path).useLines {
            var sb: StringBuilder? = null
            it.forEach { line ->
                if (line.startsWith("###")) {
                    if (sb != null) {
                        readings.add(sb.toString())
                    }
                    sb = StringBuilder()
                } else {
                    sb!!.append(line)
                    sb!!.append(System.lineSeparator())
                }
            }

            if(sb!!.isNotEmpty())
                readings.add(sb.toString())
        }
    }

    private fun nextReading(): String {
        return readings[pos].also {
            if (pos + 1 < readings.size) {
                pos++
            }
        }
    }

    fun close() {
        logger.info("Shutting down metrics reader.")
        server?.stop()
        shutdownListener.countDown()
    }
}