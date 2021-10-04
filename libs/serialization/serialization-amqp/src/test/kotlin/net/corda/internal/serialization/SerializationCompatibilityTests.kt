package net.corda.internal.serialization

import net.corda.internal.serialization.amqp.custom.ThrowableSerializer
import net.corda.internal.serialization.amqp.testutils.testDefaultFactoryNoEvolution
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@Timeout(value = 30, unit = TimeUnit.SECONDS)
class SerializationCompatibilityTests {

    @Test
	fun `fingerprint is stable`() {
        val factory = testDefaultFactoryNoEvolution().apply { register(ThrowableSerializer(this), true, true, this) }
        assertThat(factory.get(Exception::class.java).typeDescriptor.toString()).isEqualTo("net.corda:ApZ2a/36VVskaoDZMbiZ8A==")
    }
}
