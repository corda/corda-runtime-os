package net.corda.p2p.linkmanager.utilities

import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals

class LoggingInterceptor private constructor(private val testAppender: TestAppender) {

    companion object {

        fun setupLogging(): LoggingInterceptor {
            val context = LogManager.getContext(false) as LoggerContext
            context.reconfigure()
            val configuration = context.configuration
            val testAppender = TestAppender()

            configuration.rootLogger.addAppender(testAppender, null, null)
            configuration.rootLogger.level = Level.TRACE
            context.updateLoggers()
            testAppender.start()
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

    fun assertErrorContains(message: String) {
        assertEquals(Level.ERROR, testAppender.levels.single())
        assertThat(testAppender.messages.single()).contains(message)
    }

}