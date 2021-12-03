package net.corda.internal.serialization.amqp.custom

import net.corda.internal.serialization.amqp.ReusableSerialiseDeserializeAssert.Companion.serializeDeserializeAssert
import net.corda.internal.serialization.amqp.testutils.testDefaultFactory
import net.corda.internal.serialization.registerCustomSerializers
import net.corda.serialization.BaseDirectSerializer
import net.corda.serialization.BaseProxySerializer
import net.corda.serialization.InternalDirectSerializer
import net.corda.v5.serialization.MissingSerializerException
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
        it.register(SerializerForInterface(), it)
        it.register(SerializerForAbstractClass(), it)
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

    class SerializerForInterface : BaseDirectSerializer<TestInterface>() {
        override val type: Class<TestInterface> get() = TestInterface::class.java
        override val withInheritance: Boolean get() = true


        override fun readObject(reader: InternalDirectSerializer.ReadObject): TestInterface {
            return testAnonymousClassFromInterface
        }

        override fun writeObject(obj: TestInterface, writer: InternalDirectSerializer.WriteObject) {
            writer.putAsObject(obj.booleanProperty)
        }
    }

    class SerializerForAbstractClass : BaseProxySerializer<TestAbstractClass, ProxyClass>() {
        override val type: Class<TestAbstractClass> get() = TestAbstractClass::class.java
        override val proxyType: Class<ProxyClass> get() = ProxyClass::class.java
        override val withInheritance: Boolean get() = true

        override fun fromProxy(proxy: ProxyClass): TestAbstractClass {
            return testAnonymousClassFromAbstractClass
        }
        override fun toProxy(obj: TestAbstractClass): ProxyClass {
            return ProxyClass(obj.booleanProperty)
        }
    }
}
