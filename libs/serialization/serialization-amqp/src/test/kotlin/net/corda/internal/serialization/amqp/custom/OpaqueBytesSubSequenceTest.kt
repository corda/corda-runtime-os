package net.corda.internal.serialization.amqp.custom

import net.corda.internal.serialization.amqp.ReusableSerialiseDeserializeAssert.Companion.serializeDeserializeAssert
import net.corda.v5.base.types.OpaqueBytesSubSequence
import org.junit.jupiter.api.Test
import java.nio.charset.Charset

class OpaqueBytesSubSequenceTest {
    @Test
    fun fullByteSequence() {
        serializeDeserializeAssert(OpaqueBytesSubSequence("TEST".toByteArray(Charset.defaultCharset()), 0, 4))
    }

    @Test
    fun partByteSequence() {
        serializeDeserializeAssert(OpaqueBytesSubSequence("TEST".toByteArray(Charset.defaultCharset()), 1, 2))
    }
}