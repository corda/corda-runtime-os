package net.corda.simulator.runtime.serialization

import net.corda.simulator.runtime.testutils.generateKey
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.OpaqueBytes
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.instanceOf
import org.hamcrest.Matchers.`is`
import org.junit.jupiter.api.Test
import java.security.PublicKey

class BaseSerializationServiceTest {

    companion object {
        private val serializationService = BaseSerializationService()
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

    private fun <T: Any> roundTrip(target: T, clazz : Class<T> = target.javaClass): T {
        val bytes = serializationService.serialize(target)
        return serializationService.deserialize(bytes, clazz)
    }
}