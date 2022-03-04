package net.corda.introspiciere.server

import io.restassured.RestAssured
import net.corda.introspiciere.domain.TopicDefinitionPayload
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class CreateTopicTest : IntrospiciereServerWithFakeContext() {
    @Test
    fun `create a topic only with required params`() {
        RestAssured.given()
            .body(TopicDefinitionPayload("topic1", null, null, emptyMap()))
            .`when`()
            .post("/topics")
            .then()
            .statusCode(200)

        val topic = fakeAppContext.topicGateway.findByName("topic1")
        assertNotNull(topic, "topic should have been created")
        assertEquals("topic1", topic!!.name, "topic name")
        assertEquals(1, topic.partitions, "topic partitions")
        assertEquals(1, topic.replicas, "topic replicas")
    }
}