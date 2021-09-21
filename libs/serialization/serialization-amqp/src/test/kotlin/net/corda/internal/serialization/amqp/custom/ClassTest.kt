package net.corda.internal.serialization.amqp.custom

import net.corda.internal.serialization.amqp.ReusableSerialiseDeserializeAssert.Companion.serializeDeserializeAssert
import org.junit.jupiter.api.Test

class ClassTest {
    @Test
    fun string() {
        serializeDeserializeAssert(String::class.java)
    }
    @Test
    fun stringArray() {
        serializeDeserializeAssert(arrayOf("a").javaClass)
    }
    @Test
    fun intArray() {
        serializeDeserializeAssert(intArrayOf(1).javaClass)
    }
    @Test
    fun list() {
        serializeDeserializeAssert(listOf(1, 2, 3).javaClass)
    }
    @Test
    fun nonSystemClass() {
        serializeDeserializeAssert(ClassTest::class.java)
    }
}

