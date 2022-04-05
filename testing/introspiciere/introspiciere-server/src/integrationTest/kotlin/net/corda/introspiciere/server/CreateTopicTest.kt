package net.corda.introspiciere.server

import io.restassured.RestAssured
import io.restassured.RestAssured.`when`
import io.restassured.RestAssured.given
import net.corda.data.demo.DemoRecord
import net.corda.introspiciere.domain.KafkaMessage
import net.corda.introspiciere.domain.TopicDefinitionPayload
import net.corda.introspiciere.domain.toByteArray
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class CreateTopicTest : IntrospiciereServerWithFakeContext() {
    @Test
    fun `create a topic only with required params`() {
        given()
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

    @Test
    fun `create a topic with optional params`() {
        given()
            .body(TopicDefinitionPayload("topic1", 3, 2, mapOf("key" to "value")))
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

class WriteMessateTest : IntrospiciereServerWithFakeContext() {
    @Test
    fun `I can write a message`() {
        val g = given()
            .body(KafkaMessage(
                "topic1",
                "key1",
                DemoRecord(11).toByteBuffer().toByteArray(),
                DemoRecord::class.qualifiedName!!
            ))

        val w = `when`()
            .post("/topics/topic1/messages")
    }
}