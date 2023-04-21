package net.corda.internal.serialization.amqp.custom

import net.corda.internal.serialization.amqp.ReusableSerialiseDeserializeAssert.Companion.serializeDeserializeAssert
import org.junit.jupiter.api.Test
import java.time.LocalDate

class LocalDateTest {
    @Test
    fun epoch() {
        serializeDeserializeAssert(LocalDate.EPOCH)
    }
    @Test
    fun min() {
        serializeDeserializeAssert(LocalDate.MIN)
    }
    @Test
    fun max() {
        serializeDeserializeAssert(LocalDate.MAX)
    }
    @Test
    fun year2000() {
        serializeDeserializeAssert(LocalDate.of(2000, 1, 1))
    }
    @Test
    fun year3000() {
        serializeDeserializeAssert(LocalDate.of(3000, 1, 1))
    }
}