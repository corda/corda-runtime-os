package net.corda.cli.application.logger

import java.io.OutputStream
import java.io.PrintStream
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream

enum class LogLevel {
    TRACE, DEBUG, INFO, WARN, ERROR
}

class LoggerStream(
    private val logger: Logger,
    private val logLevel: LogLevel
) : OutputStream() {

    public companion object {
        fun redirectSystemAndErrorOut() {
            val sysOut: Logger = LoggerFactory.getLogger("System Out")
            val errOut: Logger = LoggerFactory.getLogger("System Err")

            System.setOut(PrintStream(LoggerStream(sysOut, LogLevel.INFO)))
            System.setErr(PrintStream(LoggerStream(errOut, LogLevel.ERROR)))
        }
    }

    private val baos = ByteArrayOutputStream(1000)

    override fun write(b: Int) {
        if (b.toChar() == '\n') {

            val line = baos.toString()
            baos.reset()

            systemOutput(logLevel, line)

        } else {
            baos.write(b)
        }
        logIfNotEmpty(logLevel, b.toString())
    }

    override fun write(b: ByteArray) {
        logIfNotEmpty(logLevel, String(b))
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        logIfNotEmpty(logLevel, String(b, off, len))
    }

    // needed to wrap slf4js log method which offers no level options
    private fun systemOutput(logLevel: LogLevel, message: String) {
        when (logLevel) {
            LogLevel.DEBUG -> logger.debug(message)
            LogLevel.ERROR -> logger.error(message)
            LogLevel.INFO -> logger.info(message)
            LogLevel.TRACE -> logger.trace(message)
            LogLevel.WARN -> logger.warn(message)
        }
    }

    private fun logIfNotEmpty(logLevel: LogLevel, message: String) {
        if (message.trim().isNotEmpty()) {
            systemOutput(logLevel, message)
        }
    }
}