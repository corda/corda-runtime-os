package net.corda.utilities

import org.slf4j.LoggerFactory
import java.io.File
import java.time.Clock

object Context {
    private val logger = LoggerFactory.getLogger("QQQ")
    private val clock = Clock.systemUTC()
    val context = ThreadLocal<String>();

    fun myLog(str: String) {
        try {
            val file = File("/tmp/logs/log.txt")
            file.parentFile.mkdirs()
            file.appendText("${clock.instant()} $str\n")
        } catch (e: Exception) {
            logger.warn("OOPS: $e - $str", e)
        }
    }
}