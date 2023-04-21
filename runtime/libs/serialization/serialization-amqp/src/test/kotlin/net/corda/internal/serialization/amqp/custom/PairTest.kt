package net.corda.internal.serialization.amqp.custom

import net.corda.internal.serialization.amqp.ReusableSerialiseDeserializeAssert
import org.junit.jupiter.api.Test

class PairTest {
    @Test
    fun pair() {
        ReusableSerialiseDeserializeAssert.serializeDeserializeAssert(Pair(1, 2))
    }
}