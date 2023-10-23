package net.corda.messaging.api.mediator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class MediatorMessageTest {
    private val payload: String = "payload"
    private val topicKey: String = "topic"
    private val topicValue: String = "topic"
    private val partitionKey: String = "partition"
    private val partitionValue: Long = 1L

    private val defaultMessage: MediatorMessage<Any> = MediatorMessage(payload, mutableMapOf(
        topicKey to topicValue,
        partitionKey to partitionValue
    ))

    @Test
    fun `Test add property (string)`() {
        val message: MediatorMessage<Any> = MediatorMessage(payload)
        message.addProperty(topicKey, topicValue)

        assertEquals(message.properties, mutableMapOf(topicKey to topicValue))
    }

    @Test
    fun `Test add property (long)`() {
        val message: MediatorMessage<Any> = MediatorMessage(payload)
        message.addProperty(partitionKey, partitionValue)

        assertEquals(message.properties, mutableMapOf(partitionKey to partitionValue))
    }

    @Test
    fun `Test create message with props`() {
        val message: MediatorMessage<Any> = MediatorMessage(payload, mutableMapOf(
            topicKey to topicValue,
            partitionKey to partitionValue
        ))

        assertEquals(message.properties, mutableMapOf(topicKey to topicValue, partitionKey to partitionValue))
    }

    @Test
    fun `Test get property, non-typed`() {
        val property = defaultMessage.getProperty(topicKey)
        assertEquals(property, topicValue)
    }

    @Test
    fun `Test get property that doesn't exist, non-typed`() {
        val ex = assertThrows(NoSuchElementException::class.java) {
            defaultMessage.getProperty("hello world")
        }

        assertEquals(ex.message, "No property found with the key: 'hello world'")
    }

    @Test
    fun `Test get property, typed`() {
        val property = defaultMessage.getProperty<String>(topicKey)
        assertEquals(property, topicValue)
        assertEquals(property::class, String::class)
    }

    @Test
    fun `Test get property that doesn't exist, typed`() {
        val ex = assertThrows(NoSuchElementException::class.java) {
            defaultMessage.getProperty<String>("hello world")
        }

        assertEquals(ex.message, "No property found with the key: 'hello world'")
    }

    @Test
    fun `Test get existing typed property, passing in the wrong type`() {
        val ex = assertThrows(ClassCastException::class.java) {
            defaultMessage.getProperty<Long>(topicKey)
        }

        assertEquals(ex.message, "Property 'topic' could not be cast to type: 'class java.lang.Long'.")
    }

    @Test
    fun `Test get existing nullable property, non-typed`() {
        val property = defaultMessage.getPropertyOrNull(topicKey)
        assertEquals(property, topicValue)
    }

    @Test
    fun `Test get existing nullable property, typed`() {
        val property = defaultMessage.getPropertyOrNull<String>(topicKey)
        assertEquals(property, topicValue)
        assertEquals(property!!::class, String::class)
    }

    @Test
    fun `Test get nullable property that doesn't exist, non-typed`() {
        val property = defaultMessage.getPropertyOrNull("hello world")
        assertNull(property)
    }

    @Test
    fun `Test get nullable property that doesn't exist, typed`() {
        val property = defaultMessage.getPropertyOrNull<String>("hello world")
        assertNull(property)
    }

    @Test
    fun `Test get existing nullable typed property, passing in the wrong type`() {
        val ex = assertThrows(ClassCastException::class.java) {
            defaultMessage.getPropertyOrNull<Long>(topicKey)
        }

        assertEquals(ex.message, "Property 'topic' could not be cast to type: 'class java.lang.Long'.")
    }
}
