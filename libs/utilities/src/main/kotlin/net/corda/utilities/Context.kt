package net.corda.utilities

import org.slf4j.LoggerFactory
import java.io.File
import java.time.Clock
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.concurrent.thread
import kotlin.concurrent.withLock

object Context {
    private val logger = LoggerFactory.getLogger("QQQ")
    private val clock = Clock.systemUTC()
    private val index = AtomicLong(0)
    private val lines = AtomicLong(0)
    private val lock = ReentrantLock()

    val context = ThreadLocal<String>();

    fun myLog(str: String) {
        try {
            lock.withLock {
                val file = File("/tmp/logs/log.$index.txt")
                file.parentFile.mkdirs()
                file.appendText("${clock.instant()} $str\n")
                if (lines.incrementAndGet() > 100000) {
                    lines.set(0)
                    index.incrementAndGet()
                    zip(file)
                }
            }
        } catch (e: Exception) {
            logger.warn("OOPS: $e - $str", e)
        }
    }

    private fun zip(input: File) {
        thread {
            val zipFile = File(input.parentFile, "${input.name}.zip")
            zipFile.outputStream().use { fileOutputStream->
                ZipOutputStream(fileOutputStream).use { zipOutputStream ->
                    val entry = ZipEntry(input.name)
                    zipOutputStream.putNextEntry(entry)
                    zipOutputStream.write(input.readBytes())
                    zipOutputStream.closeEntry()
                }
            }
            input.delete()
            val dir = File("/tmp/logs/zips")
            dir.mkdirs()
            val mvFile = File(dir, zipFile.name)
            zipFile.renameTo(mvFile)
        }

    }
}