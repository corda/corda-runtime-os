package net.corda.internal.serialization.amqp.custom

import net.corda.internal.serialization.amqp.ReusableSerialiseDeserializeAssert.Companion.serializeDeserializeAssert
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.time.Month
import java.time.YearMonth

class YearMonthTest {
    companion object {
        @JvmStatic
        fun everyMonth(): Array<Month> {
            return Month.values()

        }
    }
    @ParameterizedTest
    @MethodSource("everyMonth")
    fun twentyTwentyEveryMonth(month: Month){
        serializeDeserializeAssert(YearMonth.of(3000, month))
    }
    @Test
    fun year2000Jan() {
        serializeDeserializeAssert(YearMonth.of(2000, Month.JANUARY))
    }
    @Test
    fun year3000Jan() {
        serializeDeserializeAssert(YearMonth.of(3000, Month.JANUARY))
    }
}