package net.corda.internal.serialization.amqp.custom

import net.corda.internal.serialization.amqp.ReusableSerialiseDeserializeAssert
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.time.DayOfWeek

class DayOfWeekTest {

    companion object {
        @JvmStatic
        fun everyDay() : List<DayOfWeek> {
            return DayOfWeek.values().toList()
        }
    }

    @ParameterizedTest
    @MethodSource("everyDay")
    fun everyDayOfWeek(day : DayOfWeek) {
        ReusableSerialiseDeserializeAssert.serializeDeserializeAssert(day)
    }
}

