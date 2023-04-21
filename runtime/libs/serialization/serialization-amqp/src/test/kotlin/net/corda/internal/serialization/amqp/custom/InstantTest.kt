package net.corda.internal.serialization.amqp.custom

import net.corda.internal.serialization.amqp.ReusableSerialiseDeserializeAssert.Companion.serializeDeserializeAssert
import org.junit.jupiter.api.Test
import java.time.Instant

class InstantTest {
    @Test
    fun epoch() {
        serializeDeserializeAssert(Instant.EPOCH)
    }
    @Test
    fun max() {
        serializeDeserializeAssert(Instant.MAX)
    }
    @Test
    fun min() {
        serializeDeserializeAssert(Instant.MIN)
    }
    @Test
    fun year2000() {
        serializeDeserializeAssert(Instant.parse("2000-01-01T00:00:00.00Z"))
    }
    @Test
    fun year3000() {
        serializeDeserializeAssert(Instant.parse("3000-01-01T00:00:00.00Z"))
    }
}
