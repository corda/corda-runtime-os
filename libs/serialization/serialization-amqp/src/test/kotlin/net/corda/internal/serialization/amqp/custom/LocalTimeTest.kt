package net.corda.internal.serialization.amqp.custom

import net.corda.internal.serialization.amqp.ReusableSerialiseDeserializeAssert.Companion.serializeDeserializeAssert
import org.junit.jupiter.api.Test
import java.time.LocalTime

class LocalTimeTest {
    @Test
    fun min() {
        serializeDeserializeAssert(LocalTime.MIN)
    }
    @Test
    fun max() {
        serializeDeserializeAssert(LocalTime.MAX)
    }
    @Test
    fun noon() {
        serializeDeserializeAssert(LocalTime.NOON)
    }
    @Test
    fun midnight() {
        serializeDeserializeAssert(LocalTime.MIDNIGHT)
    }
}