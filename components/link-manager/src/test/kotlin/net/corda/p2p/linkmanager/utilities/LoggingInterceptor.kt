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

        data class LoggerMessage(val message: String, val level: Level)

        val messages = mutableListOf<LoggerMessage>()

        override fun append(event: LogEvent?) {
            event?. let {
                messages.add(LoggerMessage(it.message.formattedMessage, it.level))
            }
        }
    }

    fun reset() {
        testAppender.messages.clear()
    }

    fun assertSingleDebug(expectedMessage: String) {
        val debugs = testAppender.messages.filter { it.level == Level.DEBUG }
        assertEquals(1, debugs.size)
        assertEquals(expectedMessage, debugs.single().message)
    }

    fun assertSingleWarning(expectedMessage: String) {
        val warnings = testAppender.messages.filter { it.level == Level.WARN }
        assertEquals(1, warnings.size)
        assertEquals(expectedMessage, warnings.single().message)
    }

    fun assertSingleWarningContains(expectedMessagePart: String) {
        val warnings = testAppender.messages.filter { it.level == Level.WARN }
        assertEquals(1, warnings.size)
        assertThat(warnings.single().message).contains(expectedMessagePart)
    }

    fun assertSingleError(expectedMessage: String) {
        val errors = testAppender.messages.filter { it.level == Level.ERROR }
        assertEquals(1, errors.size)
        assertEquals(expectedMessage, errors.single().message)
    }

    fun assertErrorContains(message: String) {
        val errors = testAppender.messages.filter { it.level == Level.ERROR }
        assertEquals(1, errors.size)
        assertThat(errors.single().message).contains(message)
    }
}