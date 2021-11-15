package net.corda.internal.serialization.amqp

import net.corda.internal.serialization.amqp.testutils.deserializeAndReturnEnvelope
import net.corda.internal.serialization.amqp.testutils.testDefaultFactoryNoEvolution
import net.corda.internal.serialization.amqp.testutils.testSerializationContext
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import kotlin.test.assertTrue

@Timeout(value = 30, unit = TimeUnit.SECONDS)
class SerializeAndReturnMetadataTest {
    val factory = testDefaultFactoryNoEvolution()

    @Test
    fun `returns an empty CPK registry when a sandbox group is not provided in serialization context`() {
        data class A(val a: Int, val b: String)

        val a = A(10, "20")

        val serialised = SerializationOutput(factory).serialize(a, testSerializationContext)
        val obj = DeserializationInput(factory).deserializeAndReturnEnvelope(serialised)

        assertTrue(obj.envelope.metadata.isEmpty())
    }
}