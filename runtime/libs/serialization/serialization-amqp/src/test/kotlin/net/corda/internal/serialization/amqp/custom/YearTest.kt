package net.corda.internal.serialization.amqp.custom

import net.corda.internal.serialization.amqp.ReusableSerialiseDeserializeAssert.Companion.serializeDeserializeAssert
import org.junit.jupiter.api.Test
import java.time.Year

class YearTest {
    @Test
    fun min() {
        serializeDeserializeAssert(Year.MIN_VALUE)
    }
    @Test
    fun max() {
        serializeDeserializeAssert(Year.MAX_VALUE)
    }
    @Test
    fun year2000() {
        serializeDeserializeAssert(Year.of(2000))
    }
    @Test
    fun year3000() {
        serializeDeserializeAssert(Year.of(3000))
    }
}