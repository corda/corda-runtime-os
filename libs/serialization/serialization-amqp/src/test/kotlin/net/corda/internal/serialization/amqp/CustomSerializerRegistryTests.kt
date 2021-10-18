package net.corda.internal.serialization.amqp

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.serialization.SerializationCustomSerializer
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class CustomSerializerRegistryTests {

    private val descriptorBasedRegistry = DefaultDescriptorBasedSerializerRegistry()
    private val unit = CachingCustomSerializerRegistry(descriptorBasedRegistry)

    abstract class AbstractTestCustomSerializer<TTarget, TProxy> : SerializationCustomSerializer<TTarget, TProxy> {
        override fun fromProxy(proxy: TProxy): TTarget {
            throw UnsupportedOperationException()
        }

        override fun toProxy(obj: TTarget): TProxy {
            throw UnsupportedOperationException()
        }
    }


    @Test
    fun `a custom serializer cannot register to serialize a type already annotated with CordaSerializable`() {
        @CordaSerializable
        class AnnotatedWithCordaSerializable
        class NotAnnotatedWithCordaSerializable
        class TestCustomSerializer : AbstractTestCustomSerializer<Any, String>()

        val serializerForEverything = TestCustomSerializer()
        unit.register(serializerForEverything, true)

        assertNotNull(unit.find(NotAnnotatedWithCordaSerializable::class.java))

        assertFailsWith<IllegalCustomSerializerException> {
            unit.find(AnnotatedWithCordaSerializable::class.java)
        }
    }

    @CordaSerializable
    class MyCustomException : CordaRuntimeException("Custom exception annotated with @CordaSerializable")
    @Test
    fun `exception types can have custom serializers`() {
        class TestCustomSerializer : AbstractTestCustomSerializer<MyCustomException, String>()

        val customExceptionSerializer = TestCustomSerializer()

        unit.register(customExceptionSerializer, true)
        assertNotNull(unit.find(MyCustomException::class.java))
    }

    @CordaSerializable
    class Cash
    @Test
    fun `two custom serializers cannot register to serialize the same type`() {
        class TestCashCustomSerializer : AbstractTestCustomSerializer<Cash, String>()

        val weSerializeCash = TestCashCustomSerializer()
        val weMaliciouslySerializeCash = TestCashCustomSerializer()

        unit.run {
            register(weSerializeCash, true)
            register(weMaliciouslySerializeCash, true)
        }

        assertFailsWith<DuplicateCustomSerializerException> {
            unit.find(Cash::class.java)
        }
    }

    @Test
    fun `primitive types cannot have custom serializers`() {
        class TestCustomSerializer : AbstractTestCustomSerializer<Float, String>()
        unit.register(TestCustomSerializer(), true)

        assertFailsWith<IllegalCustomSerializerException> {
            unit.find(Float::class.java)
        }
    }

    private fun CustomSerializerRegistry.find(clazz: Class<*>): AMQPSerializer<Any>? = findCustomSerializer(clazz, clazz)
}