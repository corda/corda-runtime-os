package net.corda.simulator.runtime.serialization

import net.corda.simulator.runtime.testutils.generateKey
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.OpaqueBytes
import net.corda.v5.serialization.SerializationCustomSerializer
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.instanceOf
import org.hamcrest.Matchers.`is`
import org.junit.jupiter.api.Test
import java.security.PublicKey

class BaseSerializationServiceTest {

    companion object {
        private val serializationService = BaseSerializationService(
            listOf(CustomSerializer(), AnotherCustomSerializer())
        )
    }

    // Note we bring in all of Corda's serializers, so there's no point testing all of them; this is just
    // a subset of the most interesting ones.

    @Test
    fun `should be able to serialize throwables`() {
        assertThat(roundTrip(CordaRuntimeException("Message")), instanceOf(CordaRuntimeException::class.java))
    }

    @Test
    fun `should be able to serialize stack trace elements`() {
        val ste = StackTraceElement("my.class.Thing", "method", "Thing.kt", 1)
        assertThat(roundTrip(ste), `is`(ste))
    }

    @Test
    fun `should be able to serialize opaque bytes`() {
        val bytes = OpaqueBytes("my string".toByteArray())
        assertThat(roundTrip(bytes), `is`(bytes))
    }

    @Test
    fun `should be able to serialize public keys`() {
        val key = generateKey()
        assertThat(roundTrip(key, PublicKey::class.java), `is`(key))
    }

    @Test
    fun `should be able to use multiple custom serializer`(){
        val customSerializable = CustomSerializable(1)
        assertThat(roundTrip(customSerializable).number, `is`(1))
        val anotherCustomSerializable = AnotherCustomSerializable("Hello!")
        assertThat(roundTrip(anotherCustomSerializable).content, `is`("Hello!"))
    }

    private fun <T: Any> roundTrip(target: T, clazz : Class<T> = target.javaClass): T {
        val bytes = serializationService.serialize(target)
        return serializationService.deserialize(bytes, clazz)
    }

    class CustomSerializable (val number: Int)
    class CustomSerializableProxy(val number: Int)

    class AnotherCustomSerializable (val content: String)
    class AnotherCustomSerializableProxy(val content: String)

    private class CustomSerializer: SerializationCustomSerializer<CustomSerializable, CustomSerializableProxy>{
        override fun toProxy(obj: CustomSerializable): CustomSerializableProxy {
            return CustomSerializableProxy(obj.number)
        }
        override fun fromProxy(proxy: CustomSerializableProxy): CustomSerializable {
            return CustomSerializable(proxy.number)
        }
    }

    private class AnotherCustomSerializer:
        SerializationCustomSerializer<AnotherCustomSerializable, AnotherCustomSerializableProxy>{
        override fun toProxy(obj: AnotherCustomSerializable): AnotherCustomSerializableProxy {
            return AnotherCustomSerializableProxy(obj.content)
        }
        override fun fromProxy(proxy: AnotherCustomSerializableProxy): AnotherCustomSerializable {
            return AnotherCustomSerializable(proxy.content)
        }
    }

}