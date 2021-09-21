package net.corda.internal.serialization.amqp.custom

import net.corda.internal.serialization.amqp.ReusableSerialiseDeserializeAssert.Companion.serializeDeserializeAssert
import org.junit.jupiter.api.Test
import java.time.Month
import java.time.MonthDay

class MonthDayTest {
    @Test
    fun firstOfJanuary() {
        serializeDeserializeAssert(MonthDay.of(Month.JANUARY, 1))
    }
    @Test
    fun leapYearExtraDay() {
        serializeDeserializeAssert(MonthDay.of(Month.FEBRUARY, 29))
    }
    @Test
    fun lastDayOfAugust() {
        serializeDeserializeAssert(MonthDay.of(Month.AUGUST, 31))
    }

}