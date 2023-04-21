package net.corda.internal.serialization.amqp

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.internal.serialization.amqp.testutils.deserializeAndReturnEnvelope
import net.corda.internal.serialization.amqp.testutils.serialize
import net.corda.internal.serialization.amqp.testutils.testDefaultFactory
import net.corda.internal.serialization.amqp.testutils.testDefaultFactoryNoEvolution
import net.corda.internal.serialization.amqp.testutils.testName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@Timeout(value = 30, unit = TimeUnit.SECONDS)
class DeserializeAndReturnEnvelopeTests {
    // the 'this' reference means we can't just move this to the common test utils
    @Suppress("NOTHING_TO_INLINE")
    private inline fun classTestName(clazz: String) = "${this.javaClass.name}\$${testName()}\$$clazz"

    val factory = testDefaultFactoryNoEvolution()

    @Test
	fun oneType() {
        @CordaSerializable
        data class A(val a: Int, val b: String)

        val a = A(10, "20")

        fun serialise(clazz: Any) = SerializationOutput(factory).serialize(clazz)
        val obj = DeserializationInput(factory).deserializeAndReturnEnvelope(serialise(a))

        assertTrue(obj.obj is A)
        assertEquals(1, obj.envelope.schema.types.size)
        assertEquals(classTestName("A"), obj.envelope.schema.types.first().name)
    }

    @Test
	fun twoTypes() {
        @CordaSerializable
        data class A(val a: Int, val b: String)
        @CordaSerializable
        data class B(val a: A, val b: Float)

        val b = B(A(10, "20"), 30.0F)

        fun serialise(clazz: Any) = SerializationOutput(factory).serialize(clazz)
        val obj = DeserializationInput(factory).deserializeAndReturnEnvelope(serialise(b))

        assertTrue(obj.obj is B)
        assertEquals(2, obj.envelope.schema.types.size)
        assertNotEquals(null, obj.envelope.schema.types.find { it.name == classTestName("A") })
        assertNotEquals(null, obj.envelope.schema.types.find { it.name == classTestName("B") })
    }

    @Test
	fun unannotatedInterfaceIsNotInSchema() {
        @CordaSerializable
        data class Foo(val bar: Int) : Comparable<Foo> {
            override fun compareTo(other: Foo): Int = bar.compareTo(other.bar)
        }

        val a = Foo(123)
        val factory = testDefaultFactory()
        fun serialise(clazz: Any) = SerializationOutput(factory).serialize(clazz)
        val obj = DeserializationInput(factory).deserializeAndReturnEnvelope(serialise(a))

        assertTrue(obj.obj is Foo)
        assertEquals(1, obj.envelope.schema.types.size)
        assertNotEquals(null, obj.envelope.schema.types.find { it.name == classTestName("Foo") })
        assertEquals(null, obj.envelope.schema.types.find { it.name == "java.lang.Comparable<${classTestName("Foo")}>" })
    }
}
