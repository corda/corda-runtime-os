package net.corda.internal.serialization.amqp.custom

import net.corda.internal.serialization.amqp.ReusableSerialiseDeserializeAssert.Companion.serializeDeserializeAssert
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class OffsetDateTimeTest {
    @Test
    fun min() {
        serializeDeserializeAssert(OffsetDateTime.MIN)
    }
    @Test
    fun max() {
        serializeDeserializeAssert(OffsetDateTime.MAX)
    }

    companion object {
        @JvmStatic
        fun zoneOffsets() = ZoneOffset.getAvailableZoneIds().map { ZoneId.of(it) }
    }

    @ParameterizedTest
    @MethodSource("zoneOffsets")
    fun year2000AroundTheWorld(zoneId: ZoneId) {
        val year2000 = LocalDateTime.of(2000, 1, 1, 0, 0)
        val zoneOffset = ZoneOffset.from(zoneId.rules.getOffset(year2000))

        serializeDeserializeAssert(OffsetDateTime.of(year2000, zoneOffset))
    }
}