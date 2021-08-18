package net.corda.internal.serialization.amqp

import net.corda.v5.serialization.ClassWhitelist
import net.corda.v5.serialization.SerializationCustomSerializer
import net.corda.internal.serialization.AllWhitelist
import net.corda.internal.serialization.amqp.testutils.deserialize
import net.corda.internal.serialization.amqp.testutils.deserializeAndReturnEnvelope
import net.corda.internal.serialization.amqp.testutils.serialize
import net.corda.internal.serialization.amqp.testutils.serializeAndReturnSchema
import net.corda.internal.serialization.amqp.testutils.testDefaultFactory
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.io.NotSerializableException
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals

@Timeout(value = 30, unit = TimeUnit.SECONDS)
class CorDappSerializerTests {
    data class NeedsProxy(val a: String)

    private fun proxyFactory(
            serializers: List<SerializationCustomSerializer<*, *>>
    ) = SerializerFactoryBuilder.build(AllWhitelist).apply {
        serializers.forEach {
            registerExternal(CorDappCustomSerializer(it, this))
        }
    }

    class NeedsProxyProxySerializer : SerializationCustomSerializer<NeedsProxy, NeedsProxyProxySerializer.Proxy> {
        data class Proxy(val proxy_a_: String)

        override fun fromProxy(proxy: Proxy) = NeedsProxy(proxy.proxy_a_)
        override fun toProxy(obj: NeedsProxy) = Proxy(obj.a)
    }

    // Standard proxy serializer used internally, here for comparison purposes
    class InternalProxySerializer(factory: SerializerFactory) :
            CustomSerializer.Proxy<NeedsProxy, InternalProxySerializer.Proxy>(
                    NeedsProxy::class.java,
                    InternalProxySerializer.Proxy::class.java,
                    factory) {
        data class Proxy(val proxy_a_: String)

        override fun toProxy(obj: NeedsProxy): Proxy {
            return Proxy(obj.a)
        }

        override fun fromProxy(proxy: Proxy): NeedsProxy {
            return NeedsProxy(proxy.proxy_a_)
        }
    }

    @Test
	fun `type uses proxy`() {
        val internalProxyFactory = testDefaultFactory()
        val proxyFactory = testDefaultFactory()
        val defaultFactory = testDefaultFactory()

        val msg = "help"

        proxyFactory.registerExternal(CorDappCustomSerializer(NeedsProxyProxySerializer(), proxyFactory))
        internalProxyFactory.register(InternalProxySerializer(internalProxyFactory))

        val needsProxy = NeedsProxy(msg)

        val bAndSProxy = SerializationOutput(proxyFactory).serializeAndReturnSchema(needsProxy)
        val bAndSInternal = SerializationOutput(internalProxyFactory).serializeAndReturnSchema(needsProxy)
        val bAndSDefault = SerializationOutput(defaultFactory).serializeAndReturnSchema(needsProxy)

        val objFromDefault = DeserializationInput(defaultFactory).deserializeAndReturnEnvelope(bAndSDefault.obj)
        val objFromInternal = DeserializationInput(internalProxyFactory).deserializeAndReturnEnvelope(bAndSInternal.obj)
        val objFromProxy = DeserializationInput(proxyFactory).deserializeAndReturnEnvelope(bAndSProxy.obj)

        assertEquals(msg, objFromDefault.obj.a)
        assertEquals(msg, objFromInternal.obj.a)
        assertEquals(msg, objFromProxy.obj.a)
    }

    @Test
	fun proxiedTypeIsNested() {
        data class A(val a: Int, val b: NeedsProxy)

        val factory = testDefaultFactory()
        factory.registerExternal(CorDappCustomSerializer(NeedsProxyProxySerializer(), factory))

        val tv1 = 100
        val tv2 = "pants schmants"
        val bAndS = SerializationOutput(factory).serializeAndReturnSchema(A(tv1, NeedsProxy(tv2)))

        val objFromDefault = DeserializationInput(factory).deserializeAndReturnEnvelope(bAndS.obj)

        assertEquals(tv1, objFromDefault.obj.a)
        assertEquals(tv2, objFromDefault.obj.b.a)
    }

    @Test
	fun testWithWhitelistNotAllowed() {
        data class A(val a: Int, val b: NeedsProxy)

        class WL : ClassWhitelist {
            private val allowedClasses = emptySet<String>()

            override fun hasListed(type: Class<*>): Boolean = type.name in allowedClasses
        }

        val whitelist = WL()
        val factory = SerializerFactoryBuilder.build(whitelist)
        factory.registerExternal(CorDappCustomSerializer(NeedsProxyProxySerializer(), factory))

        val tv1 = 100
        val tv2 = "pants schmants"
        Assertions.assertThatThrownBy {
            SerializationOutput(factory).serialize(A(tv1, NeedsProxy(tv2)))
        }.isInstanceOf(NotSerializableException::class.java)
    }

    @Test
	fun testWithWhitelistAllowed() {
        data class A(val a: Int, val b: NeedsProxy)

        class WL : ClassWhitelist {
            private val allowedClasses = setOf<String>(
                    A::class.java.name,
                    NeedsProxy::class.java.name)

            override fun hasListed(type: Class<*>): Boolean = type.name in allowedClasses
        }

        val whitelist = WL()
        val factory = SerializerFactoryBuilder.build(whitelist)
        factory.registerExternal(CorDappCustomSerializer(NeedsProxyProxySerializer(), factory))

        val tv1 = 100
        val tv2 = "pants schmants"
        val obj = DeserializationInput(factory).deserialize(
                SerializationOutput(factory).serialize(A(tv1, NeedsProxy(tv2))))

        assertEquals(tv1, obj.a)
        assertEquals(tv2, obj.b.a)
    }

    // The custom type not being whitelisted won't matter here because the act of adding a
    // custom serializer bypasses the whitelist
    @Test
	fun testWithWhitelistAllowedOuterOnly() {
        data class A(val a: Int, val b: NeedsProxy)

        class WL : ClassWhitelist {
            // explicitly don't add NeedsProxy
            private val allowedClasses = setOf<String>(A::class.java.name)

            override fun hasListed(type: Class<*>): Boolean = type.name in allowedClasses
        }

        val whitelist = WL()
        val factory = SerializerFactoryBuilder.build(whitelist)
        factory.registerExternal(CorDappCustomSerializer(NeedsProxyProxySerializer(), factory))

        val tv1 = 100
        val tv2 = "pants schmants"
        val obj = DeserializationInput(factory).deserialize(
                SerializationOutput(factory).serialize(A(tv1, NeedsProxy(tv2))))

        assertEquals(tv1, obj.a)
        assertEquals(tv2, obj.b.a)
    }

    data class NeedsProxyGen<T>(val a: T)

    class NeedsProxyGenProxySerializer :
            SerializationCustomSerializer<NeedsProxyGen<*>, NeedsProxyGenProxySerializer.Proxy> {
        data class Proxy(val proxy_a_: Any?)

        override fun fromProxy(proxy: Proxy) = NeedsProxyGen(proxy.proxy_a_)
        override fun toProxy(obj: NeedsProxyGen<*>) = Proxy(obj.a)
    }

    // Tests CORDA-1747
    @Test
	fun proxiedGeneric() {
        val proxyFactory = proxyFactory(listOf(NeedsProxyGenProxySerializer()))

        val msg = "help"


        val blob = SerializationOutput(proxyFactory).serialize(NeedsProxyGen(msg))
        val objFromProxy = DeserializationInput(proxyFactory).deserialize(blob)

        assertEquals(msg, objFromProxy.a)
    }

    // Need an interface to restrict the generic to in the following test
    interface Bound {
        fun wibbleIt(): String
    }

    // test class to be serialized whose generic arg is restricted to instances
    // of the Bound interface declared above
    data class NeedsProxyGenBounded<T : Bound>(val a: T)

    // Proxy for our test class
    class NeedsProxyGenBoundedProxySerializer :
            SerializationCustomSerializer<NeedsProxyGenBounded<*>,
                    NeedsProxyGenBoundedProxySerializer.Proxy> {
        data class Proxy(val proxy_a_: Bound)

        override fun fromProxy(proxy: Proxy) = NeedsProxyGenBounded(proxy.proxy_a_)
        override fun toProxy(obj: NeedsProxyGenBounded<*>) = Proxy(obj.a)
    }

    // Since we need a value for our test class that implements the interface
    // we're restricting its generic property to, this is that class.
    data class HasWibble(val a: String) : Bound {
        override fun wibbleIt() = "wibble it, just a little bit!."
    }

    // Because we're enforcing all classes have proxy serializers we
    // have to implement this to avoid the factory erroneously failing
    class HasWibbleProxy :
            SerializationCustomSerializer<HasWibble, HasWibbleProxy.Proxy> {
        data class Proxy(val proxy_a_: String)

        override fun fromProxy(proxy: Proxy) = HasWibble(proxy.proxy_a_)
        override fun toProxy(obj: HasWibble) = Proxy(obj.a)
    }

    // Tests CORDA-1747 - Finally the actual bound generics test, on failure it will throw
    @Test
	fun proxiedBoundedGeneric() {
        val proxyFactory = proxyFactory(listOf(NeedsProxyGenBoundedProxySerializer(), HasWibbleProxy()))

        val blob = SerializationOutput(proxyFactory).serialize(NeedsProxyGenBounded(HasWibble("A")))
        val objFromProxy = DeserializationInput(proxyFactory).deserialize(blob)

        assertEquals("A", objFromProxy.a.a)
    }

    data class NeedsProxyGenContainer<T>(val a: List<T>)

    class NeedsProxyGenContainerProxySerializer :
            SerializationCustomSerializer<NeedsProxyGenContainer<*>,
                    NeedsProxyGenContainerProxySerializer.Proxy> {
        data class Proxy(val proxy_a_: List<*>)

        override fun fromProxy(proxy: Proxy) = NeedsProxyGenContainer(proxy.proxy_a_)
        override fun toProxy(obj: NeedsProxyGenContainer<*>) = Proxy(obj.a)
    }

    // Tests CORDA-1747
    @Test
	fun proxiedGenericContainer() {
        val proxyFactory = proxyFactory(listOf(NeedsProxyGenContainerProxySerializer()))

        val blob1 = SerializationOutput(proxyFactory).serialize(NeedsProxyGenContainer(listOf(1, 2, 3)))
        val obj1 = DeserializationInput(proxyFactory).deserialize(blob1)

        assertEquals(listOf(1, 2, 3), obj1.a)

        val blob2 = SerializationOutput(proxyFactory).serialize(NeedsProxyGenContainer(listOf("1", "2", "3")))
        val obj2 = DeserializationInput(proxyFactory).deserialize(blob2)

        assertEquals(listOf("1", "2", "3"), obj2.a)

        val blob3 = SerializationOutput(proxyFactory).serialize(NeedsProxyGenContainer(listOf("1", 2, "3")))
        val obj3 = DeserializationInput(proxyFactory).deserialize(blob3)

        assertEquals(listOf("1", 2, "3"), obj3.a)
    }

    open class Base<T>(val a: T)
    class Derived<T>(a: T, val b: String) : Base<T>(a)

    class BaseProxy :
            SerializationCustomSerializer<Base<*>, BaseProxy.Proxy> {
        data class Proxy(val proxy_a_: Any?)

        override fun fromProxy(proxy: Proxy) = Base(proxy.proxy_a_)
        override fun toProxy(obj: Base<*>) = Proxy(obj.a)
    }

    class DerivedProxy :
            SerializationCustomSerializer<Derived<*>, DerivedProxy.Proxy> {
        data class Proxy(val proxy_a_: Any?, val proxy_b_: String)

        override fun fromProxy(proxy: Proxy) = Derived(proxy.proxy_a_, proxy.proxy_b_)
        override fun toProxy(obj: Derived<*>) = Proxy(obj.a, obj.b)
    }

    // Tests CORDA-1747
    @Test
	fun proxiedInheritableGenerics() {
        val proxyFactory = proxyFactory(listOf(BaseProxy(), DerivedProxy()))

        val blob1 = SerializationOutput(proxyFactory).serialize(Base(100L))
        DeserializationInput(proxyFactory).deserialize(blob1)
        val blob2 = SerializationOutput(proxyFactory).serialize(Derived(100L, "Hey pants"))
        DeserializationInput(proxyFactory).deserialize(blob2)
    }
}