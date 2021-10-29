package net.corda.internal.serialization.amqp

import net.corda.internal.serialization.SerializationContext
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.serialization.SerializationCustomSerializer
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import kotlin.test.assertFailsWith

class CustomSerializerRegistryTests {

    private val descriptorBasedRegistry = DefaultDescriptorBasedSerializerRegistry()
    private val unit = CachingCustomSerializerRegistry(descriptorBasedRegistry)

    @Test
    fun `a custom serializer cannot register to serialize a type already annotated with CordaSerializable`() {
        @CordaSerializable
        class AnnotatedWithCordaSerializable
        class NotAnnotatedWithCordaSerializable
        class EverythingCustomSerializer(factory: SerializerFactory) : CustomSerializer.Proxy<Any, String>(
            clazz = Any::class.java,
            proxyClass = String::class.java,
            factory,
            withInheritance = true
        ) {
            override fun toProxy(obj: Any, context: SerializationContext): String
                = throw UnsupportedOperationException()
            override fun fromProxy(proxy: String): Any
                = throw UnsupportedOperationException()
        }

        val serializerForEverything = EverythingCustomSerializer(mock())
        unit.register(serializerForEverything)

        assertSame(serializerForEverything, unit.find(NotAnnotatedWithCordaSerializable::class.java))

        assertFailsWith<IllegalCustomSerializerException> {
            unit.find(AnnotatedWithCordaSerializable::class.java)
        }
    }

    class MyCustomException : CordaRuntimeException("Custom exception")

    @Test
    fun `exception types can have custom serializers`() {
        class ExceptionCustomSerializer(factory: SerializerFactory) : CustomSerializer.Proxy<MyCustomException, String>(
            clazz = MyCustomException::class.java,
            proxyClass = String::class.java,
            factory,
            withInheritance = true
        ) {
            override fun toProxy(obj: MyCustomException, context: SerializationContext): String
                = throw UnsupportedOperationException()
            override fun fromProxy(proxy: String): MyCustomException
                = throw UnsupportedOperationException()
        }

        val customExceptionSerializer = ExceptionCustomSerializer(mock())

        unit.register(customExceptionSerializer)
        assertSame(customExceptionSerializer, unit.find(MyCustomException::class.java))
    }

    open class Cash
    class CashSubclass : Cash()

    @Test
    fun `two custom serializers cannot register to serialize the same type`() {
        class CordaCashCustomSerializer(factory: SerializerFactory) : CustomSerializer.Proxy<Cash, String>(
            clazz = Cash::class.java,
            proxyClass = String::class.java,
            factory,
            withInheritance = true
        ) {
            override fun toProxy(obj: Cash, context: SerializationContext): String
                = throw UnsupportedOperationException()
            override fun fromProxy(proxy: String): Cash
                = throw UnsupportedOperationException()
        }

        class EvilCorDappCashSerializer : SerializationCustomSerializer<CashSubclass, String> {
            override fun fromProxy(proxy: String): CashSubclass
                = throw UnsupportedOperationException()
            override fun toProxy(obj: CashSubclass): String
                = throw UnsupportedOperationException()
        }

        val weSerializeCash = CordaCashCustomSerializer(mock())
        val weMaliciouslySerializeCash = EvilCorDappCashSerializer()

        unit.run {
            // First registration wins!
            register(weSerializeCash)
            // This second registration should be ignored.
            registerExternal(CorDappCustomSerializer(weMaliciouslySerializeCash, mock()))
        }

        assertSame(weSerializeCash, unit.find(Cash::class.java))
    }

    @Test
    fun `primitive types cannot have custom serializers`() {
        class TestCustomSerializer(factory: SerializerFactory) : CustomSerializer.Proxy<Float, String>(
            clazz = Float::class.java,
            proxyClass = String::class.java,
            factory,
            withInheritance = true
        ) {
            override fun toProxy(obj: Float, context: SerializationContext): String
                = throw UnsupportedOperationException()
            override fun fromProxy(proxy: String): Float
                = throw UnsupportedOperationException()
        }
        unit.register(TestCustomSerializer(mock()))

        assertFailsWith<IllegalCustomSerializerException> {
            unit.find(Float::class.java)
        }
    }

    private fun CustomSerializerRegistry.find(clazz: Class<*>): AMQPSerializer<Any>? = findCustomSerializer(clazz, clazz)
}
