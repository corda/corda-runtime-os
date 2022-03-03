package net.corda.introspiciere.cli.topics

import net.corda.introspiciere.cli.internalMain
import net.corda.introspiciere.domain.TopicDescription
import net.corda.testdoubles.http.StartFakeHttpServer
import net.corda.testdoubles.http.fakeHttpServer
import net.corda.testdoubles.streams.StandardStreams
import net.corda.testdoubles.streams.inMemoryStdout
import net.corda.testdoubles.util.parse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@StartFakeHttpServer
@StandardStreams
class DescribeTopicCommandTest {
    @Test
    fun `i can describe a topic`() {
        fakeHttpServer.handle("get", "topics/topic1") {
            json(TopicDescription("id1", "name1", 5, 6))
        }

        internalMain(
            *parse("topics describe"),
            *parse("--endpoint ${fakeHttpServer.endpoint}"),
            *parse("--topic topic1"),
            overrideStdout = inMemoryStdout.outputStream
        )

        assertEquals("""
            id: "id1"
            name: "name1"
            partitions: 5
            replicas: 6
            """.trimIndent(),
            inMemoryStdout.readText().trim(),
            "expected output"
        )
    }
}