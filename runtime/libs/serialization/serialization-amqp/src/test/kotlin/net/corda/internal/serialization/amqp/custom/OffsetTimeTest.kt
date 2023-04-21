package net.corda.internal.serialization.amqp.custom

import net.corda.internal.serialization.amqp.ReusableSerialiseDeserializeAssert.Companion.serializeDeserializeAssert
import org.junit.jupiter.api.Test
import java.time.LocalTime
import java.time.OffsetTime
import java.time.ZoneOffset

class OffsetTimeTest {
    @Test
    fun min() {
        serializeDeserializeAssert(OffsetTime.MIN)
    }
    @Test
    fun max() {
        serializeDeserializeAssert(OffsetTime.MAX)
    }

    @Test
    fun threePm() {
        serializeDeserializeAssert(
            OffsetTime.of(
                LocalTime.of(15, 0),
                ZoneOffset.UTC
            )
        )
    }
}