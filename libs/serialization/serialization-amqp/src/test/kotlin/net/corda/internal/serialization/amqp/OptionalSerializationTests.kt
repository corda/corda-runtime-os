package net.corda.internal.serialization.amqp

import net.corda.internal.serialization.AllWhitelist
import net.corda.internal.serialization.amqp.custom.OptionalSerializer
import net.corda.internal.serialization.amqp.testutils.TestSerializationOutput
import net.corda.internal.serialization.amqp.testutils.deserialize
import net.corda.internal.serialization.amqp.testutils.testDefaultFactory
import net.corda.internal.serialization.amqp.testutils.testSerializationContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.Optional
import java.util.concurrent.TimeUnit

@Timeout(value = 30, unit = TimeUnit.SECONDS)
class OptionalSerializationTests {

    @Test
    fun setupEnclosedSerializationTest() {
        fun `java optionals should serialize`() {
            val factory = SerializerFactoryBuilder.build(AllWhitelist, testSerializationContext.currentSandboxGroup())
            factory.register(OptionalSerializer(), factory,)
            val obj = Optional.ofNullable("YES")
            val bytes = TestSerializationOutput(true, factory).serialize(obj)
            val deserializerFactory = testDefaultFactory().apply {
                register(OptionalSerializer(), this,)
            }

            val deserialized = DeserializationInput(factory).deserialize(bytes)
            val deserialized2 = DeserializationInput(deserializerFactory).deserialize(bytes)
            assertThat(deserialized).isEqualTo(deserialized2)
            assertThat(obj).isEqualTo(deserialized2)
        }

        `java optionals should serialize`()
    }
}