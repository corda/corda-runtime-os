package net.corda.internal.serialization.amqp.custom

import java.lang.reflect.Type
import net.corda.internal.serialization.SerializationContext
import net.corda.internal.serialization.amqp.CustomSerializer
import net.corda.internal.serialization.amqp.DeserializationInput
import net.corda.internal.serialization.amqp.Metadata
import net.corda.internal.serialization.amqp.ReusableSerialiseDeserializeAssert.Companion.serializeDeserializeAssert
import net.corda.internal.serialization.amqp.Schema
import net.corda.internal.serialization.amqp.SerializationOutput
import net.corda.internal.serialization.amqp.SerializationSchemas
import net.corda.internal.serialization.amqp.SerializerFactory
import net.corda.internal.serialization.amqp.testutils.testDefaultFactory
import net.corda.internal.serialization.registerCustomSerializers
import net.corda.v5.serialization.MissingSerializerException
import org.apache.qpid.proton.codec.Data
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AnonymousClassTest {

    // Tests

    @Test
    fun anonymousClassBasedOnInterface() {
        serializeDeserializeAssert(testAnonymousClassFromInterface, factoryWithSerializersRegistered)
    }

    @Test
    fun anonymousClassBasedOnAbstractClass() {
        serializeDeserializeAssert(testAnonymousClassFromAbstractClass, factoryWithSerializersRegistered)
    }

    @Test
    fun anonymousClassBasedOnInterfaceWithoutCustomSerializer() {
        assertThrows<MissingSerializerException> {
            serializeDeserializeAssert(testAnonymousClassFromInterface)
        }
    }

    @Test
    fun anonymousClassBasedOnAbstractClassWithoutCustomSerializer() {
        assertThrows<MissingSerializerException> {
            serializeDeserializeAssert(testAnonymousClassFromAbstractClass)
        }
    }

    // Test classes, objects and interfaces to support the tests

    companion object {
        private val testAnonymousClassFromInterface: TestInterface = object : TestInterface {
            override val booleanProperty: Boolean
                get() = true

            override fun somethingToImplement(): Boolean {
                return booleanProperty
            }
        }

        private val testAnonymousClassFromAbstractClass: TestAbstractClass = object : TestAbstractClass() {
            override val booleanProperty: Boolean
                get() = true

            override fun somethingToImplement(): Boolean {
                return booleanProperty
            }
        }
    }

    private val factoryWithSerializersRegistered = testDefaultFactory().also {
        registerCustomSerializers(it)
        it.register(SerializerForInterface())
        it.register(SerializerForAbstractClass(it))
    }

    class ProxyClass(val value: Boolean)

    interface TestInterface {
        val booleanProperty: Boolean
        fun somethingToImplement(): Boolean
    }

    abstract class TestAbstractClass {
        abstract val booleanProperty: Boolean
        abstract fun somethingToImplement(): Boolean
    }

    class SerializerForInterface : CustomSerializer.Implements<TestInterface>(TestInterface::class.java) {
        override val schemaForDocumentation = Schema(emptyList())

        override fun readObject(
            obj: Any,
            serializationSchemas: SerializationSchemas,
            metadata: Metadata,
            input: DeserializationInput,
            context: SerializationContext
        ): TestInterface {
            return testAnonymousClassFromInterface
        }

        override fun writeDescribedObject(
            obj: TestInterface,
            data: Data,
            type: Type,
            output: SerializationOutput,
            context: SerializationContext
        ) {
            data.putBoolean(obj.booleanProperty)
        }
    }
    class SerializerForAbstractClass(factory: SerializerFactory) : CustomSerializer.Proxy<TestAbstractClass, ProxyClass>(
        clazz = TestAbstractClass::class.java,
        proxyClass = ProxyClass::class.java,
        factory = factory,
        withInheritance = true
    ) {
        override fun fromProxy(proxy: ProxyClass): TestAbstractClass {
            return testAnonymousClassFromAbstractClass
        }
        override fun toProxy(obj: TestAbstractClass, context: SerializationContext): ProxyClass {
            return ProxyClass(obj.booleanProperty)
        }
    }
}
