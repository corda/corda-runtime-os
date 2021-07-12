package net.corda.p2p.linkmanager.utilities

import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.junit.jupiter.api.Assertions.assertEquals

class LoggingInterceptor private constructor(private var testAppender: TestAppender) {

    companion object {
        fun setupLogging(): LoggingInterceptor {
            LogManager.shutdown()
            val context = LogManager.getContext(false) as LoggerContext
            val configuration = context.configuration
            val testAppender = TestAppender()
            testAppender.start()
            configuration.rootLogger.addAppender(testAppender, null, null)
            configuration.rootLogger.level = Level.TRACE
            context.updateLoggers()
            return LoggingInterceptor(testAppender)
        }
    }

    private class TestAppender: AbstractAppender("TestAppender", null, null, false, null) {
        val messages = mutableListOf<String>()
        val levels = mutableListOf<Level>()

        override fun append(event: LogEvent?) {
            event?. let {
                messages.add(it.message.formattedMessage)
                levels.add(it.level)
            }
        }
    }

    fun reset() {
        testAppender.messages.clear()
        testAppender.levels.clear()
    }

    fun assertSingleWarning(expectedMessage: String) {
        assertEquals(Level.WARN, testAppender.levels.single())
        assertEquals(expectedMessage, testAppender.messages.single())
    }

}