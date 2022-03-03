package net.corda.introspiciere.junit

import net.corda.introspiciere.domain.TopicDefinitionPayload
import net.corda.testdoubles.http.StartFakeHttpServer
import net.corda.testdoubles.http.bodyAs
import net.corda.testdoubles.http.fakeHttpServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@StartFakeHttpServer
internal class CreateTopicTest {

    private lateinit var client: IntrospiciereClient

    @BeforeEach
    fun beforeEach() {
        client = IntrospiciereClient(fakeHttpServer.endpoint)
    }

    @Test
    fun `create a topic with all options`() {
        fakeHttpServer.handle("post", "/topics") {
            val payload = bodyAs<TopicDefinitionPayload>()
            assertEquals("topic1", payload.name)
            assertEquals(5, payload.partitions)
            assertEquals(2.toShort(), payload.replicationFactor)
            assertEquals(mapOf("cleanup.policy" to "compact", "segment.ms" to "300000"), payload.config)
        }

        client.createTopic("topic1", 5, 2, mapOf(
            "cleanup.policy" to "compact",
            "segment.ms" to 300_000
        ))
    }
}
