package net.corda.internal.serialization.amqp

import net.corda.internal.serialization.accessPropertyDescriptors
import net.corda.internal.serialization.amqp.ReusableSerialiseDeserializeAssert.Companion.serializeDeserializeAssert
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

@Timeout(value = 30)
class SetsSerializationTests {

    @Test
    fun `check set can be serialized as root of serialization graph`() {
        serializeDeserializeAssert(emptySet<Int>())
        serializeDeserializeAssert(setOf(1))
        serializeDeserializeAssert(setOf(1, 2))
    }

    open class P
    class VarOfP(var p: Set<P>)

    @Test
    fun `type variance on setter getter pair does not fail validation`() {
        assertThat(VarOfP::class.java.accessPropertyDescriptors()).containsKey("p")
    }
}