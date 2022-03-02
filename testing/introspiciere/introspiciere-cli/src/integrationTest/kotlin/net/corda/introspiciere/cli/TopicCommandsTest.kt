package net.corda.introspiciere.cli

import net.corda.testdoubles.http.StartFakeHttpServer
import net.corda.testdoubles.http.fakeHttpServer
import net.corda.testdoubles.streams.StandardStreams
import net.corda.testdoubles.streams.inMemoryStdout
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

@StartFakeHttpServer
@StandardStreams
class TopicCommandsTest {

    @Test
    fun `list all topics`() {
        fakeHttpServer.addGetHandler("/topics") {
            json(setOf("topic1", "topic2"))
        }

        internalMain("topics", "list", "--endpoint", fakeHttpServer.endpoint,
            overrideStdout = inMemoryStdout.outputStream)

        Assertions.assertEquals("topic1\ntopic2\n", inMemoryStdout.readText())
    }

    @Test
    fun `list all topics - empty`() {
        fakeHttpServer.addGetHandler("/topics") {
            json(emptySet<String>())
        }

        internalMain("topics", "list", "--endpoint", fakeHttpServer.endpoint,
            overrideStdout = inMemoryStdout.outputStream)

        Assertions.assertEquals("", inMemoryStdout.readText())
    }
}