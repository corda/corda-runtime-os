package net.corda.internal.serialization

import net.corda.internal.serialization.amqp.testutils.testDefaultFactoryNoEvolution
import net.corda.v5.base.annotations.CordaSerializable
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@Timeout(value = 30, unit = TimeUnit.SECONDS)
class SerializationCompatibilityTests {

    @CordaSerializable
    class StableFingerprintTest

    @Test
	fun `fingerprint is stable`() {
        val factory = testDefaultFactoryNoEvolution()
        assertThat(factory.get(StableFingerprintTest::class.java).typeDescriptor.toString()).isEqualTo("net.corda:zbvSPLDAvP9+Hlml5i3ZOw==")
    }
}
