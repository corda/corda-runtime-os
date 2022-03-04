package net.corda.introspiciere.server

import io.restassured.RestAssured
import net.corda.introspiciere.domain.TopicDefinition
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class DeleteTopicTest : IntrospiciereServerWithFakeContext() {
    @Test
    fun `delete topic`() {
        fakeAppContext.topicGateway.create(TopicDefinition("topic1"))

        RestAssured.delete("/topics/topic1")
            .then()
            .statusCode(200)

        Assertions.assertNull(
            fakeAppContext.topicGateway.findByName("topic1"),
            "topic should not exist"
        )
    }

    @Test
    fun `delete non existing topic`() {
        RestAssured.delete("/topics/topic1")
            .then()
            .statusCode(404)
    }
}