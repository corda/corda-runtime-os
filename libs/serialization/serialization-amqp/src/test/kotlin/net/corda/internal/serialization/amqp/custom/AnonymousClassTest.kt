package net.corda.internal.serialization.amqp.custom

import net.corda.internal.serialization.amqp.ReusableSerialiseDeserializeAssert.Companion.serializeDeserializeAssert
import net.corda.internal.serialization.amqp.testutils.testDefaultFactory
import net.corda.internal.serialization.registerCustomSerializers
import net.corda.v5.serialization.MissingSerializerException
import net.corda.v5.serialization.SerializationCustomSerializer
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
        it.register(SerializerForInterface(), true)
        it.register(SerializerForAbstractClass(), true)
    }

    class ProxyClass

    interface TestInterface {
        val booleanProperty: Boolean
        fun somethingToImplement(): Boolean
    }

    abstract class TestAbstractClass {
        abstract val booleanProperty: Boolean
        abstract fun somethingToImplement(): Boolean
    }

    class SerializerForInterface : SerializationCustomSerializer<TestInterface, ProxyClass> {
        override fun fromProxy(proxy: ProxyClass): TestInterface {
            return testAnonymousClassFromInterface
        }
        override fun toProxy(obj: TestInterface): ProxyClass {
            return ProxyClass()
        }
    }
    class SerializerForAbstractClass : SerializationCustomSerializer<TestAbstractClass, ProxyClass> {
        override fun fromProxy(proxy: ProxyClass): TestAbstractClass {
            return testAnonymousClassFromAbstractClass
        }
        override fun toProxy(obj: TestAbstractClass): ProxyClass {
            return ProxyClass()
        }
    }
}
