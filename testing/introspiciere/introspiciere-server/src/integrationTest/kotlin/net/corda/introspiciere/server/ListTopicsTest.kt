package net.corda.introspiciere.server

import io.restassured.RestAssured.get
import net.corda.introspiciere.domain.TopicDefinition
import org.hamcrest.CoreMatchers.`is`
import org.junit.jupiter.api.Test

class ListTopicsTest : IntrospiciereServerWithFakeContext() {
    @Test
    fun `list topics`() {
        fakeAppContext.topicGateway.create(TopicDefinition("topic1"))
        fakeAppContext.topicGateway.create(TopicDefinition("topic2"))

        get("/topics")
            .then()
            .statusCode(200)
            .body("", `is`(listOf("topic1", "topic2")))
    }

    @Test
    fun `empty list`() {
        get("/topics")
            .then()
            .statusCode(200)
            .body("", `is`(emptyList<String>()))
    }
}
