package net.corda.introspiciere.server

import io.restassured.RestAssured
import net.corda.introspiciere.domain.TopicDefinition
import org.hamcrest.CoreMatchers
import org.junit.jupiter.api.Test

class DescribeTopicTest : IntrospiciereServerWithFakeContext() {
    @Test
    fun `describe topic`() {
        fakeAppContext.topicGateway.create(TopicDefinition("topic1", 3, 2))
        RestAssured.get("/topics/topic1")
            .then()
            .statusCode(200)
            .body("id", CoreMatchers.notNullValue())
            .body("name", CoreMatchers.`is`("topic1"))
            .body("partitions", CoreMatchers.`is`(3))
            .body("replicas", CoreMatchers.`is`(2))
    }

    @Test
    fun `topic not found`() {
        RestAssured.get("/topics/topic1")
            .then()
            .statusCode(404)
    }
}