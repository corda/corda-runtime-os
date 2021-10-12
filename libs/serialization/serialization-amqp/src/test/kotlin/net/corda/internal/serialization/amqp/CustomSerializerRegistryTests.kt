package net.corda.internal.serialization.amqp

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.serialization.SerializationContext
import org.apache.qpid.proton.amqp.Symbol
import org.apache.qpid.proton.codec.Data
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import java.lang.reflect.Type
import kotlin.test.assertFailsWith

class CustomSerializerRegistryTests {

    private val descriptorBasedRegistry = DefaultDescriptorBasedSerializerRegistry()
    private val unit = CachingCustomSerializerRegistry(descriptorBasedRegistry)

    class TestCustomSerializer(descriptorString: String, private val serializerFor: (Class<*>) -> Boolean): CustomSerializer<Any>() {

        override val descriptor: Descriptor get() = throw UnsupportedOperationException()
        override val schemaForDocumentation: Schema get() = throw UnsupportedOperationException()
        override val type: Type get() = Any::class.java
        override val typeDescriptor: Symbol = Symbol.valueOf(descriptorString)

        override fun writeDescribedObject(obj: Any, data: Data, type: Type, output: SerializationOutput, context: SerializationContext) {
            throw UnsupportedOperationException()
        }

        override fun isSerializerFor(clazz: Class<*>): Boolean = serializerFor(clazz)

        override fun writeClassInfo(output: SerializationOutput) {
            throw UnsupportedOperationException()
        }

        override fun readObject(obj: Any, serializationSchemas: SerializationSchemas, metadata: Metadata, input: DeserializationInput,
                                context: SerializationContext): Any {
            throw UnsupportedOperationException()
        }
    }

    @Test
    fun `a custom serializer cannot register to serialize a type already annotated with CordaSerializable`() {
        val serializerForEverything = TestCustomSerializer("a") { true }
        unit.register(serializerForEverything)

        @CordaSerializable
        class AnnotatedWithCordaSerializable
        class NotAnnotatedWithCordaSerializable

        assertSame(serializerForEverything, unit.find(NotAnnotatedWithCordaSerializable::class.java))

        assertFailsWith<IllegalCustomSerializerException> {
            unit.find(AnnotatedWithCordaSerializable::class.java)
        }
    }

    @Test
    fun `exception types can have custom serializers`() {
        @CordaSerializable
        class MyCustomException : CordaRuntimeException("Custom exception annotated with @CordaSerializable")

        val customExceptionSerializer = TestCustomSerializer("a") { type -> type == MyCustomException::class.java }
        unit.register(customExceptionSerializer)

        assertSame(customExceptionSerializer, unit.find(MyCustomException::class.java))
    }

    @Test
    fun `two custom serializers cannot register to serialize the same type`() {
        @CordaSerializable
        class Cash

        val weSerializeCash = TestCustomSerializer("a") { type -> type == Cash::class.java }
        val weMaliciouslySerializeCash = TestCustomSerializer("b") { type -> type == Cash::class.java }

        unit.run {
            register(weSerializeCash)
            register(weMaliciouslySerializeCash)
        }

        assertFailsWith<DuplicateCustomSerializerException> {
            unit.find(Cash::class.java)
        }
    }

    @Test
    fun `primitive types cannot have custom serializers`() {
        unit.register(TestCustomSerializer("a") { type -> type == Float::class.java })

        assertFailsWith<IllegalCustomSerializerException> {
            unit.find(Float::class.java)
        }
    }

    private fun CustomSerializerRegistry.find(clazz: Class<*>): AMQPSerializer<Any> = findCustomSerializer(clazz, clazz)!!
}