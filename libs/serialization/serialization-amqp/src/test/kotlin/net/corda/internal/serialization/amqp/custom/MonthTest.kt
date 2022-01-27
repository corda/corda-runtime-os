package net.corda.internal.serialization.amqp.custom

import net.corda.internal.serialization.amqp.ReusableSerialiseDeserializeAssert
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.time.Month

class MonthTest {

    companion object {
        @JvmStatic
        fun everyMonth() : List<Month> {
            return Month.values().toList()
        }
    }

    @ParameterizedTest
    @MethodSource("everyMonth")
    fun everyDayOfWeek(month : Month) {
        ReusableSerialiseDeserializeAssert.serializeDeserializeAssert(month)
    }
}