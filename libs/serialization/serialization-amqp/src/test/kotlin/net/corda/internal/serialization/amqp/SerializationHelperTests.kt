package net.corda.internal.serialization.amqp

import com.google.common.reflect.TypeToken
import net.corda.v5.base.annotations.CordaSerializable
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertNotEquals

class SerializationHelperTests {
    @Test
    fun `asClass should correctly convert type to class`() {
        val type = object : TypeToken<List<String>>() {}.type
        val clazz = type.asClass()

        assertEquals(List::class.java, clazz)
    }

    @Test
    fun `isArray should correctly determine if type is an array type`() {
        val arrayType = object : TypeToken<Array<String>>() {}.type
        val listType = object : TypeToken<List<String>>() {}.type

        val isArray = arrayType.isArray()
        val isNotArray = listType.isArray()

        assertTrue(isArray)
        assertTrue(!isNotArray)
    }

    @Test
    fun `isArray should correctly determine if type is an array type for c-style arrays`() {
        val defaultArrayType = object : TypeToken<Array<Int>>() {}.type
        val cStyleArrayType = intArrayOf(1, 2, 3).javaClass
        val listType = object : TypeToken<List<String>>() {}.type

        assertNotEquals(defaultArrayType, cStyleArrayType)

        val isArray = cStyleArrayType.isArray()
        val isNotArray = listType.isArray()

        assertTrue(isArray)
        assertTrue(!isNotArray)
    }

    @Test
    fun `isSubClassOf should correctly determine if type is a subclass of another type`() {
        val subType = object : TypeToken<List<String>>() {}.type
        val superType = object : TypeToken<Collection<String>>() {}.type

        val isSubClass = subType.isSubClassOf(superType)
        val isNotSubClass = superType.isSubClassOf(subType)

        assertTrue(isSubClass)
        assertTrue(!isNotSubClass)
    }

    @Test
    fun `requireCordaSerializable should not throw an exception for CordaSerializable types`() {
        @CordaSerializable
        class TestCordaSerializableType
        assertDoesNotThrow {
            requireCordaSerializable(TestCordaSerializableType::class.java)
        }
    }

    @Test
    fun `requireCordaSerializable should throw an exception for non-CordaSerializable types`() {
        val nonCordaSerializableType = Object::class.java

        val exception = assertThrows(AMQPNotSerializableException::class.java) {
            requireCordaSerializable(nonCordaSerializableType)
        }

        assertEquals(
            "Class \"class java.lang.Object\" is not annotated with @CordaSerializable.",
            exception.msg
        )
    }

    @Test
    fun `hasCordaSerializable should correctly determine if a type is CordaSerializable`() {
        @CordaSerializable
        class TestCordaSerializableType
        class TestNonCordaSerializableType

        assertTrue(hasCordaSerializable(TestCordaSerializableType::class.java))
        assertTrue(!hasCordaSerializable(TestNonCordaSerializableType::class.java))
    }

    @Test
    fun `hasCordaSerializable should correctly determine if a type is derived from a CordaSerializable type`() {
        @CordaSerializable
        open class TestCordaSerializable
        class TestInheritedCordaSerializableType : TestCordaSerializable()

        assertTrue(hasCordaSerializable(TestCordaSerializable::class.java))
        assertTrue(hasCordaSerializable(TestInheritedCordaSerializableType::class.java))
    }
}
