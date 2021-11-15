package net.corda.internal.serialization.amqp.custom

import net.corda.internal.serialization.amqp.IllegalCustomSerializerException
import net.corda.internal.serialization.amqp.SerializationOutput
import net.corda.internal.serialization.amqp.testutils.testDefaultFactory
import net.corda.internal.serialization.amqp.testutils.testSerializationContext
import net.corda.v5.serialization.SerializationCustomSerializer
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.fail
import org.mockito.kotlin.mock
import java.security.PrivateKey

class PrivateKeyTest {

    class PrivateKeySerializer : SerializationCustomSerializer<PrivateKey, PrivateKeySerializer.PrivateKeyProxy> {
        override fun toProxy(obj: PrivateKey): PrivateKeyProxy = PrivateKeyProxy()
        override fun fromProxy(proxy: PrivateKeyProxy): PrivateKey = fail { "Will not reach here" }
        class PrivateKeyProxy
    }

    val factory = testDefaultFactory().apply { registerExternal(PrivateKeySerializer()) }

    @Test
    @Timeout(5)
    fun `Should throw IllegalStateException when serializing a private key`() {
        val output = SerializationOutput(factory)
        val privateKey: PrivateKey = mock()
        Assertions.assertThatThrownBy { output.serialize(privateKey, testSerializationContext) }
            .isInstanceOf(IllegalCustomSerializerException::class.java)
    }
}