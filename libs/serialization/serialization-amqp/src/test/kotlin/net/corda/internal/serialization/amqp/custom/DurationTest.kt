package net.corda.internal.serialization.amqp.custom

import net.corda.internal.serialization.amqp.ReusableSerialiseDeserializeAssert.Companion.serializeDeserializeAssert
import org.junit.jupiter.api.Test
import java.time.Duration

class DurationTest {
    @Test
    fun zero() {
        serializeDeserializeAssert(Duration.ZERO)
    }

    @Test
    fun oneSecond() {
        serializeDeserializeAssert(Duration.ofSeconds(1))
    }

    @Test
    fun minusOneSecond() {
        serializeDeserializeAssert(Duration.ofSeconds(-1))
    }

    @Test
    fun maxSeconds() {
        serializeDeserializeAssert(Duration.ofSeconds(Long.MAX_VALUE))
    }

    @Test
    fun oneMinute() {
        serializeDeserializeAssert(Duration.ofMinutes(1))
    }

    @Test
    fun oneHour() {
        serializeDeserializeAssert(Duration.ofHours(1))
    }

    @Test
    fun oneDay() {
        serializeDeserializeAssert(Duration.ofDays(1))
    }
}

