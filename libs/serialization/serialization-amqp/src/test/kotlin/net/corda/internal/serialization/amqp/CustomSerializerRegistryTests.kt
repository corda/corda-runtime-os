package net.corda.internal.serialization.amqp

import net.corda.internal.serialization.amqp.standard.CustomSerializer
import net.corda.serialization.BaseProxySerializer
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.serialization.SerializationCustomSerializer
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import kotlin.test.assertFailsWith

class CustomSerializerRegistryTests {

    private val descriptorBasedRegistry = DefaultDescriptorBasedSerializerRegistry()
    private val unit = CachingCustomSerializerRegistry(descriptorBasedRegistry, mock())

    @Test
    fun `a custom serializer cannot register to serialize a type already annotated with CordaSerializable`() {
        @CordaSerializable
        class AnnotatedWithCordaSerializable
        class NotAnnotatedWithCordaSerializable
        class EverythingCustomSerializer : BaseProxySerializer<Any, String>() {
            override val type: Class<Any> get() = Any::class.java
            override val proxyType: Class<String> get() = String::class.java
            override val withInheritance: Boolean get() = true

            override fun toProxy(obj: Any): String
                = throw UnsupportedOperationException()
            override fun fromProxy(proxy: String): Any
                = throw UnsupportedOperationException()
        }

        val serializerForEverything = CustomSerializer.Proxy(EverythingCustomSerializer(), mock())
        unit.register(serializerForEverything)

        assertSame(serializerForEverything, unit.find(NotAnnotatedWithCordaSerializable::class.java))

        assertFailsWith<IllegalCustomSerializerException> {
            unit.find(AnnotatedWithCordaSerializable::class.java)
        }
    }

    class MyCustomException : CordaRuntimeException("Custom exception")

    @Test
    fun `exception types can have custom serializers`() {
        class ExceptionCustomSerializer : BaseProxySerializer<MyCustomException, String>() {
            override val type: Class<MyCustomException> get() = MyCustomException::class.java
            override val proxyType: Class<String> get() = String::class.java
            override val withInheritance: Boolean get() = true

            override fun toProxy(obj: MyCustomException): String
                = throw UnsupportedOperationException()
            override fun fromProxy(proxy: String): MyCustomException
                = throw UnsupportedOperationException()
        }

        val customExceptionSerializer = CustomSerializer.Proxy(ExceptionCustomSerializer(), mock())

        unit.register(customExceptionSerializer)
        assertSame(customExceptionSerializer, unit.find(MyCustomException::class.java))
    }

    open class Cash
    class CashSubclass : Cash()

    @Test
    fun `two custom serializers cannot register to serialize the same type`() {
        class CordaCashCustomSerializer : BaseProxySerializer<Cash, String>() {
            override val type: Class<Cash> get() = Cash::class.java
            override val proxyType: Class<String> get() = String::class.java
            override val withInheritance: Boolean get() = true

            override fun toProxy(obj: Cash): String
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

        val weSerializeCash = CustomSerializer.Proxy(CordaCashCustomSerializer(), mock())
        val weMaliciouslySerializeCash = EvilCorDappCashSerializer()

        unit.run {
            // First registration wins!
            register(weSerializeCash)
            // This second registration should be ignored.
            registerExternal(weMaliciouslySerializeCash, mock())
        }

        assertSame(weSerializeCash, unit.find(Cash::class.java))
    }

    @Test
    fun `primitive types cannot have custom serializers`() {
        class TestCustomSerializer: BaseProxySerializer<Float, String>() {
            override val type: Class<Float> get() = Float::class.java
            override val proxyType: Class<String> get() = String::class.java
            override val withInheritance: Boolean get() = true

            override fun toProxy(obj: Float): String
                = throw UnsupportedOperationException()
            override fun fromProxy(proxy: String): Float
                = throw UnsupportedOperationException()
        }
        unit.register(TestCustomSerializer(), mock())

        assertFailsWith<IllegalCustomSerializerException> {
            unit.find(Float::class.java)
        }
    }

    private fun CustomSerializerRegistry.find(clazz: Class<*>): AMQPSerializer<Any>? = findCustomSerializer(clazz, clazz)
}
