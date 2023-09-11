package net.corda.messagebus.api.producer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class CordaMessageTest {
    private val payload: String = "payload"
    private val topicKey: String = "topic"
    private val topicValue: String = "topic"
    private val partitionKey: String = "partition"
    private val partitionValue: Long = 1L

    private val defaultMessage: CordaMessage<Any> = CordaMessage(payload, mutableMapOf(
        topicKey to topicValue,
        partitionKey to partitionValue
    ))

    @Test
    fun `Test add property (string)`() {
        val message: CordaMessage<Any> = CordaMessage(payload)
        message.addProperty(Pair(topicKey, topicValue))

        assertEquals(message.props, mutableMapOf(topicKey to topicValue))
    }

    @Test
    fun `Test add property (long)`() {
        val message: CordaMessage<Any> = CordaMessage(payload)
        message.addProperty(Pair(partitionKey, partitionValue))

        assertEquals(message.props, mutableMapOf(partitionKey to partitionValue))
    }

    @Test
    fun `Test create message with props`() {
        val message: CordaMessage<Any> = CordaMessage(payload, mutableMapOf(
            topicKey to topicValue,
            partitionKey to partitionValue
        ))

        assertEquals(message.props, mutableMapOf(topicKey to topicValue, partitionKey to partitionValue))
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
        val ex = assertThrows(ClassCastException::class.java) {
            defaultMessage.getPropertyOrNull<String>("hello world")
        }

        assertEquals(ex.message, "Property 'hello world' could not be cast to type: 'class java.lang.String'. " +
                "Ensure the property is not null."
        )
    }

    @Test
    fun `Test get existing nullable typed property, passing in the wrong type`() {
        val ex = assertThrows(ClassCastException::class.java) {
            defaultMessage.getPropertyOrNull<Long>(topicKey)
        }

        assertEquals(ex.message, "Property 'topic' could not be cast to type: 'class java.lang.Long'. " +
                "Ensure the property is not null."
        )
    }
}
