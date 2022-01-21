package net.corda.internal.serialization.amqp

import net.corda.internal.serialization.amqp.ReusableSerialiseDeserializeAssert.Companion.serializeDeserializeAssert
import net.corda.v5.base.annotations.CordaSerializable
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

    @CordaSerializable
    data class UuidContainer(val uuid: UUID)
}