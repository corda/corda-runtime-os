package net.corda.cli.application.logger

import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.appender.FileAppender
import org.apache.logging.log4j.core.layout.PatternLayout
import java.io.File


class LogToFileAppender {
    companion object {
        fun enableLogToFile(filePath: File, level: String?) {
            val ctx = LogManager.getContext(false) as LoggerContext
            val config = ctx.configuration

            val layout = PatternLayout.newBuilder()
                .withPattern("%d{yyyy-MM-dd HH:mm:ss.SSS} %level [%t] [%c] - %msg%n")
                .build()

            val logToFileAppender = FileAppender.newBuilder()
                .setName("logToFile")
                .withFileName(filePath.absolutePath.toString())
                .setLayout(layout)
                .setConfiguration(config)
                .build()

            logToFileAppender.start()

            val logLevel = if(level.isNullOrEmpty()) Level.INFO else Level.getLevel(level)

            config.addAppender(logToFileAppender)
            config.rootLogger.addAppender(logToFileAppender, logLevel, null)
            ctx.updateLoggers()
        }
    }
}