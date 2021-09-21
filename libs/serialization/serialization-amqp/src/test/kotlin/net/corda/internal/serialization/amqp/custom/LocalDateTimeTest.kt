package net.corda.internal.serialization.amqp.custom

import net.corda.internal.serialization.amqp.ReusableSerialiseDeserializeAssert.Companion.serializeDeserializeAssert
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class LocalDateTimeTest {
    @Test
    fun min() {
        serializeDeserializeAssert(LocalDateTime.MIN)
    }
    @Test
    fun max() {
        serializeDeserializeAssert(LocalDateTime.MAX)
    }
    @Test
    fun year2000ThreePM() {
        serializeDeserializeAssert(LocalDateTime.of(2000, 1, 1, 15, 0))
    }
    @Test
    fun year3000ThreePM() {
        serializeDeserializeAssert(LocalDateTime.of(3000, 1, 1, 15, 0))
    }
}