package net.corda.internal.serialization.amqp

import net.corda.internal.serialization.amqp.ReusableSerialiseDeserializeAssert.Companion.serializeDeserialize
import net.corda.internal.serialization.amqp.ReusableSerialiseDeserializeAssert.Companion.serializeDeserializeAssert
import net.corda.internal.serialization.amqp.testutils.testDefaultFactory
import net.corda.internal.serialization.registerCustomSerializers
import net.corda.v5.serialization.SerializationCustomSerializer
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.io.NotSerializableException
import kotlin.test.assertFailsWith

class SingletonSerializeAsTokenTest {

    private companion object {
        val testDefaultFactory
            get() = testDefaultFactory(externalCustomSerializerAllowed = { true })
    }

    @Test
    fun `serializing SingletonSerializeAsToken fails`() {
        val singletonSerializeAsToken = mock(SingletonSerializeAsToken::class.java)
        assertFailsWith<NotSerializableException>("Attempt to serialise SingletonSerializeAsToken") {
            serializeDeserialize(singletonSerializeAsToken)
        }
    }

    @Test
    fun `adding custom serializer for a SingletonSerializeAsToken serializes that SingletonSerializeAsToken`() {
        val factoryWithSerializersRegistered = testDefaultFactory.also {
            registerCustomSerializers(it)
            it.registerExternal(ServiceSerializer(), it)
        }

        val service = Service(5)
        serializeDeserializeAssert(service, factoryWithSerializersRegistered)
    }

    @Test
    fun `SingletonSerializeAsToken fails to serialize if custom serializer is provided for other SingletonSerializeAsToken`() {
        val factoryWithSerializersRegistered = testDefaultFactory.also {
            registerCustomSerializers(it)
            it.registerExternal(ServiceSerializer(), it)
        }

        val otherSingletonSerializeAsToken = mock(SingletonSerializeAsToken::class.java)
        assertFailsWith<NotSerializableException>("Attempt to serialise SingletonSerializeAsToken") {
            serializeDeserialize(otherSingletonSerializeAsToken, factoryWithSerializersRegistered)
        }
    }

    data class Service(val i: Int) : SingletonSerializeAsToken

    class ServiceProxy(val i: Int)

    class ServiceSerializer : SerializationCustomSerializer<Service, ServiceProxy> {
        override fun fromProxy(proxy: ServiceProxy): Service = Service(proxy.i)

        override fun toProxy(obj: Service): ServiceProxy = ServiceProxy(obj.i)
    }
}