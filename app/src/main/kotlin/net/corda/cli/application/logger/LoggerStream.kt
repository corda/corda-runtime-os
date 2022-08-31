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
    private val logLevel: LogLevel
) : OutputStream() {

    public companion object {
        val sysOut: Logger = LoggerFactory.getLogger("SystemOut")
        val errOut: Logger = LoggerFactory.getLogger("SystemErr")
        fun redirectSystemAndErrorOut() {
            System.setOut(PrintStream(LoggerStream(LogLevel.INFO)))
            System.setErr(PrintStream(LoggerStream(LogLevel.ERROR)))
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
            LogLevel.ERROR -> errOut.error(message)
            LogLevel.INFO -> sysOut.info(message)
        }
    }

    private fun logIfNotEmpty(logLevel: LogLevel, message: String) {
        if (message.trim().isNotEmpty()) {
            systemOutput(logLevel, message)
        }
    }
}