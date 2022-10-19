package net.corda.internal.serialization.amqp.custom

import net.corda.internal.serialization.amqp.IllegalCustomSerializerException
import net.corda.internal.serialization.amqp.SerializationOutput
import net.corda.internal.serialization.amqp.helper.testSerializationContext
import net.corda.internal.serialization.amqp.testutils.testDefaultFactory
import net.corda.v5.serialization.SerializationCustomSerializer
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.fail
import java.security.PrivateKey

class PrivateKeyTest {

    class PrivateKeySerializer : SerializationCustomSerializer<MockPrivateKey, PrivateKeySerializer.PrivateKeyProxy> {
        override fun toProxy(obj: MockPrivateKey): PrivateKeyProxy = PrivateKeyProxy()
        override fun fromProxy(proxy: PrivateKeyProxy): MockPrivateKey = fail { "Will not reach here" }
        class PrivateKeyProxy
    }

    val factory = testDefaultFactory().apply { registerExternal(PrivateKeySerializer(), this) }

    class MockPrivateKey(private val algorithm: String, private val format: String, private val encoded: ByteArray) : PrivateKey {
        override fun getAlgorithm(): String = algorithm
        override fun getFormat(): String = format
        override fun getEncoded(): ByteArray = encoded
    }

    @Test
    @Timeout(5)
    fun `Should throw IllegalCustomSerializerException when serializing a private key`() {
        val output = SerializationOutput(factory)
        val privateKey: PrivateKey = MockPrivateKey("", "", byteArrayOf())
        Assertions.assertThatThrownBy { output.serialize<PrivateKey>(privateKey, testSerializationContext) }
            .isInstanceOf(IllegalCustomSerializerException::class.java)
    }
}