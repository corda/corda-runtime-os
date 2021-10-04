package net.corda.internal.serialization.amqp.custom

import net.corda.internal.serialization.amqp.ReusableSerialiseDeserializeAssert.Companion.serializeDeserializeAssert
import org.junit.jupiter.api.Test
import java.time.Period

class PeriodTest {
    @Test
    fun zero() {
        serializeDeserializeAssert(Period.ZERO)
    }
    @Test
    fun oneThousandYears() {
        serializeDeserializeAssert(Period.ofYears(1000))
    }
    @Test
    fun oneDay() {
        serializeDeserializeAssert(Period.ofDays(1))
    }
    @Test
    fun oneWeek() {
        serializeDeserializeAssert(Period.ofWeeks(1))
    }
}