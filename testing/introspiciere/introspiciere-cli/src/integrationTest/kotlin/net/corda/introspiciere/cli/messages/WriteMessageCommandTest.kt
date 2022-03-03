package net.corda.introspiciere.cli.messages

import net.corda.data.demo.DemoRecord
import net.corda.introspiciere.cli.WriteCommand
import net.corda.introspiciere.cli.internalMain
import net.corda.introspiciere.domain.KafkaMessage
import net.corda.introspiciere.junit.toByteArray
import net.corda.testdoubles.http.StartFakeHttpServer
import net.corda.testdoubles.http.bodyAs
import net.corda.testdoubles.http.fakeHttpServer
import net.corda.testdoubles.streams.StandardStreams
import net.corda.testdoubles.streams.inMemoryStderr
import net.corda.testdoubles.streams.inMemoryStdin
import net.corda.testdoubles.streams.inMemoryStdout
import net.corda.testdoubles.util.parse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

@StartFakeHttpServer
@StandardStreams
class WriteMessageCommandTest {
    @Test
    fun `no input returns an exception`() {
        internalMain(
            *parse("write --endpoint ${fakeHttpServer.endpoint}"),
            *parse("--topic topic1"),
            *parse("--schema net.corda.data.demo.DemoRecord"),
            overrideStderr = inMemoryStderr.outputStream
        )

        assertEquals(
            WriteCommand.messages.unexpectedEndOfFileMessage,
            inMemoryStderr.readText().trim(),
            "expected stderr"
        )
    }

    @Test
    fun `input file does not exist`() {
        val filename = "non-existing-file.txt"

        internalMain(
            *parse("write --endpoint ${fakeHttpServer.endpoint}"),
            *parse("--topic topic1"),
            *parse("--schema net.corda.data.demo.DemoRecord"),
            *parse("--file $filename"),
            overrideStderr = inMemoryStderr.outputStream
        )

        assertEquals(
            WriteCommand.messages.fileNotFoundMessage.format(filename),
            inMemoryStderr.readText().trim(),
            "expected stderr"
        )
    }

    @Test
    fun `write a message from stdin`() {
        val value = DemoRecord(99)

        fakeHttpServer.handle("post", "/topics/topic1/messages") {
            val message = bodyAs<KafkaMessage>()
            assertEquals("topic1", message.topic, "topic name")
            assertNull(message.key, "message key")
            assertEquals(value.toByteBuffer().toByteArray().toList(), message.schema.toList())
            assertEquals(DemoRecord::class.qualifiedName, message.schemaClass)
        }

        value.toString().let(inMemoryStdin::writeText)

        internalMain(
            *parse("write"),
            *parse("--endpoint ${fakeHttpServer.endpoint}"),
            *parse("--topic topic1"),
            *parse("--schema net.corda.data.demo.DemoRecord"),
            overrideStdin = inMemoryStdin.inputStream,
            overrideStdout = inMemoryStdout.outputStream
        )

        assertEquals(
            WriteCommand.messages.successMessage.format("topic1"),
            inMemoryStdout.readText().trim()
        )
    }
}