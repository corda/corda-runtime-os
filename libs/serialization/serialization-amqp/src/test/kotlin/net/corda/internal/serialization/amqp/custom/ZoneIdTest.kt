package net.corda.internal.serialization.amqp.custom

import net.corda.internal.serialization.amqp.ReusableSerialiseDeserializeAssert.Companion.serializeDeserializeAssert
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.time.ZoneId

class ZoneIdTest {

    companion object {
        @JvmStatic
        fun everyZoneId(): List<ZoneId> {
            return ZoneId.getAvailableZoneIds().map { ZoneId.of(it) }
        }
    }

    @ParameterizedTest
    @MethodSource("everyZoneId")
    fun everyZoneId(zoneId: ZoneId) {
        serializeDeserializeAssert(zoneId)
    }
}