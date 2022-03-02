package net.corda.introspiciere.cli.topics

import net.corda.introspiciere.cli.internalMain
import net.corda.testdoubles.http.StartFakeHttpServer
import net.corda.testdoubles.http.fakeHttpServer
import net.corda.testdoubles.streams.StandardStreams
import net.corda.testdoubles.streams.inMemoryStdout
import net.corda.testdoubles.util.parse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@StartFakeHttpServer
@StandardStreams
class ListTopicCommandTest {

    @Test
    fun `list all topics`() {
        fakeHttpServer.handle("get", "/topics") {
            json(setOf("topic1", "topic2"))
        }

        internalMain(
            *"topics list --endpoint ${fakeHttpServer.endpoint}".parse(),
            overrideStdout = inMemoryStdout.outputStream
        )

        assertEquals(
            "topic1\ntopic2\n",
            inMemoryStdout.readText(), "expected output"
        )
    }

    @Test
    fun `list all topics - empty`() {
        fakeHttpServer.handle("get", "/topics") {
            json(emptySet<String>())
        }

        internalMain(
            *"topics list --endpoint ${fakeHttpServer.endpoint}".parse(),
            overrideStdout = inMemoryStdout.outputStream
        )

        assertTrue(
            inMemoryStdout.readText().isEmpty(),
            "empty output expected"
        )
    }
}
