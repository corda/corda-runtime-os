package net.corda.internal.serialization.amqp

import net.corda.internal.serialization.amqp.ReusableSerialiseDeserializeAssert.Companion.serializeDeserializeAssert
import org.junit.jupiter.api.Test
import java.util.UUID

class UuidTest {
    @Test
    fun random() {
        serializeDeserializeAssert(UUID.randomUUID())
    }

    @Test
    fun insideDataClass() {
        serializeDeserializeAssert(UuidContainer(UUID.randomUUID()))
    }

    data class UuidContainer(val uuid: UUID)
}