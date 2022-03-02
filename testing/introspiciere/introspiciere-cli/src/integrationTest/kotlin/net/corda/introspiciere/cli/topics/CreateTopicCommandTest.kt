package net.corda.introspiciere.cli.topics

import net.corda.introspiciere.cli.internalMain
import net.corda.introspiciere.domain.TopicDefinitionPayload
import net.corda.testdoubles.http.StartFakeHttpServer
import net.corda.testdoubles.http.bodyAs
import net.corda.testdoubles.http.fakeHttpServer
import net.corda.testdoubles.streams.StandardStreams
import net.corda.testdoubles.streams.inMemoryStdout
import net.corda.testdoubles.util.parse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@StartFakeHttpServer
@StandardStreams
class CreateTopicCommandTest {
    @Test
    fun `create a topic`() {
        fakeHttpServer.handle("post", "/topics") {
            val payload = bodyAs<TopicDefinitionPayload>()
            assertEquals("topic1", payload.name)
            assertEquals(5, payload.partitions)
            assertEquals(2.toShort(), payload.replicationFactor)
            assertEquals(mapOf("cleanup.policy" to "compact", "segment.ms" to "300000"), payload.config)
        }

        internalMain(
            *("""topics create --endpoint ${fakeHttpServer.endpoint} --topic topic1 --partitions 5 """ +
                    """--replication-factor 2 --config "cleanup.policy=compact" --config "segment.ms=300000"""").parse(),
            overrideStdout = inMemoryStdout.outputStream
        )

        assertEquals(
            String.format(CreateTopicCommand.successMessage, "topic1"),
            inMemoryStdout.readText().trim(),
            "expected output"
        )
    }
}