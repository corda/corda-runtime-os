package net.corda.internal.serialization.amqp.custom

import net.corda.internal.serialization.amqp.AMQPSerializer
import net.corda.internal.serialization.amqp.BytesAndSchemas
import net.corda.internal.serialization.amqp.DeserializationInput
import net.corda.internal.serialization.amqp.SerializationOutput
import net.corda.internal.serialization.amqp.SerializerFactory
import net.corda.internal.serialization.amqp.TypeNotation
import net.corda.internal.serialization.amqp.testutils.testDefaultFactory
import net.corda.internal.serialization.amqp.testutils.testSerializationContext
import net.corda.serialization.BaseDirectSerializer
import net.corda.serialization.InternalDirectSerializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import java.util.UUID

class InternalDirectSerializerTest {
    /**
     * This [Example] requires a custom serializer.
     */
    private open class Example(uuid: UUID) {
        val id: UUID = uuid

        override fun equals(other: Any?): Boolean {
            return (this === other) || ((other is Example) && id == other.id)
        }

        override fun hashCode() = id.hashCode()
    }

    private class SubExample(uuid: UUID) : Example(uuid)

    /**
     * Custom serializer for [Example] that
     * serializes as a single [UUID] field.
     *
     * Configurable for testing purposes.
     */
    private class ExampleDirectSerializer(
        override val withInheritance: Boolean,
        override val revealSubclasses: Boolean
    ) : BaseDirectSerializer<Example>() {
        override val type: Class<Example> get() = Example::class.java

        override fun writeObject(obj: Example, writer: InternalDirectSerializer.WriteObject) {
            writer.putAsObject(obj.id)
        }

        override fun readObject(reader: InternalDirectSerializer.ReadObject): Example {
            return Example(uuid = reader.getAs(UUID::class.java))
        }
    }

    private lateinit var factory: SerializerFactory

    private fun register(serializer: InternalDirectSerializer<out Any>) {
        factory.register(serializer, factory)
    }

    private fun <T: Any> serializeAndReturnSchema(obj: T): BytesAndSchemas<T> {
        return SerializationOutput(factory).serializeAndReturnSchema(obj, testSerializationContext)
    }

    private fun <T: Any> findCustomSerializer(obj: T): AMQPSerializer<Any> {
        return factory.findCustomSerializer(obj::class.java, obj::class.java)
            ?: fail("No custom serializer found")
    }

    private fun <T: Any> assertSerializeAndDeserialize(input: T) {
        val bytes = SerializationOutput(factory).serialize(input, testSerializationContext)
        val output = DeserializationInput(factory).deserialize(bytes, Any::class.java, testSerializationContext)
        assertEquals(input, output)
    }

    @BeforeEach
    fun setup() {
        factory = testDefaultFactory()
    }

    @Test
    fun testDirectSerializeAndDeserializeWithoutInheritance() {
        register(ExampleDirectSerializer(withInheritance = false, revealSubclasses = false))
        assertSerializeAndDeserialize(Example(uuid = UUID.randomUUID()))
    }

    @Test
    fun testDirectSerializeAndDeserializeWithInheritance() {
        register(ExampleDirectSerializer(withInheritance = true, revealSubclasses = false))
        assertSerializeAndDeserialize(SubExample(uuid = UUID.randomUUID()))
    }

    @Test
    fun testSubclassCanBeRevealed() {
        register(ExampleDirectSerializer(withInheritance = true, revealSubclasses = true))
        val example = SubExample(uuid = UUID.randomUUID())

        val schemas = serializeAndReturnSchema(example)
        assertThat(schemas.schema.types.map(TypeNotation::name)).contains(example::class.java.name)

        val serializer = findCustomSerializer(example)
        assertThat(serializer.type).isSameAs(SubExample::class.java)
    }

    @Test
    fun testSubclassCanBeConcealed() {
        register(ExampleDirectSerializer(withInheritance = true, revealSubclasses = false))
        val example = SubExample(uuid = UUID.randomUUID())

        val schemas = serializeAndReturnSchema(example)
        assertThat(schemas.schema.types.map(TypeNotation::name)).doesNotContain(example::class.java.name)

        val serializer = findCustomSerializer(example)
        assertThat(serializer.type).isSameAs(Example::class.java)
    }
}
