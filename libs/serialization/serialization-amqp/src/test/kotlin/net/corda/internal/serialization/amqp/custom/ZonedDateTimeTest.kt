package net.corda.internal.serialization.amqp.custom

import net.corda.internal.serialization.amqp.ReusableSerialiseDeserializeAssert.Companion.serializeDeserializeAssert
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.time.ZoneId
import java.time.ZonedDateTime

class ZonedDateTimeTest {

    companion object {
        @JvmStatic
        fun everyZoneId(): List<ZoneId> {
            return ZoneId.getAvailableZoneIds().map { ZoneId.of(it) }
        }
    }

    @ParameterizedTest
    @MethodSource("everyZoneId")
    fun year2000EveryZoneId(zoneId: ZoneId) {
        serializeDeserializeAssert(ZonedDateTime.of(2000, 1, 1, 0, 0, 0, 0, zoneId))
    }

    @ParameterizedTest
    @MethodSource("everyZoneId")
    fun year3000EveryZoneId(zoneId: ZoneId) {
        serializeDeserializeAssert(ZonedDateTime.of(3000, 1, 1, 0, 0, 0, 0, zoneId))
    }

}
