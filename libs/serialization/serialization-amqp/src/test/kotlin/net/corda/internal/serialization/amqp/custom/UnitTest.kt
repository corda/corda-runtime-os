package net.corda.internal.serialization.amqp.custom

import net.corda.internal.serialization.amqp.ReusableSerialiseDeserializeAssert
import org.junit.jupiter.api.Test

class UnitTest {
    @Test
    fun unit() {
        ReusableSerialiseDeserializeAssert.serializeDeserializeAssert(Unit)
    }
}