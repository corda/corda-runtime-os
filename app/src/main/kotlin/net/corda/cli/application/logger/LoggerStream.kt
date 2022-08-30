package net.corda.cli.application.logger

import java.io.OutputStream
import java.io.PrintStream
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream

enum class LogLevel {
    INFO, ERROR
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

    private val byteArrayOutputStream = ByteArrayOutputStream(1000)

    override fun write(byte: Int) {
        if (byte.toChar() == '\n') {

            val line = byteArrayOutputStream.toString()
            byteArrayOutputStream.reset()

            systemOutput(logLevel, line)

        } else {
            byteArrayOutputStream.write(byte)
        }
        logIfNotEmpty(logLevel, byte.toString())
    }

    override fun write(byteArray: ByteArray) {
        logIfNotEmpty(logLevel, String(byteArray))
    }

    override fun write(byteArray: ByteArray, off: Int, len: Int) {
        logIfNotEmpty(logLevel, String(byteArray, off, len))
    }

    // needed to wrap slf4js log method which offers no level options
    private fun systemOutput(logLevel: LogLevel, message: String) {
        when (logLevel) {
            LogLevel.ERROR -> logger.error(message)
            LogLevel.INFO -> logger.info(message)
        }
    }

    private fun logIfNotEmpty(logLevel: LogLevel, message: String) {
        if (message.trim().isNotEmpty()) {
            systemOutput(logLevel, message)
        }
    }
}