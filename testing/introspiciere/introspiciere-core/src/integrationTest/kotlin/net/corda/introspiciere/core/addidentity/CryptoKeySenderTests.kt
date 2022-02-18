package net.corda.introspiciere.core.addidentity

import net.corda.introspiciere.core.DeployKafka
import net.corda.p2p.test.KeyAlgorithm.ECDSA
import net.corda.p2p.test.KeyPairEntry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.nio.ByteBuffer

class CryptoKeySenderTests {
    @RegisterExtension
    val kafka = DeployKafka("alpha")

    @Test
    @Disabled
    fun first() {
//        kafka.client.createTopic(CryptoKeySenderImpl.topicName)

        CryptoKeySenderImpl(kafka.client).send(
            "alice", KeyPairEntry(ECDSA, "a".toByteBuffer(), "b".toByteBuffer())
        )

        val messages = kafka.client.read<String, KeyPairEntry>(CryptoKeySenderImpl.topicName, "alice")

        assertEquals(1, messages.size, "Only one messaged received")
        assertEquals(ECDSA, messages.single().keyAlgo)
        assertEquals("a".toByteBuffer(), messages.single().publicKey)
        assertEquals("b".toByteBuffer(), messages.single().privateKey)
    }

    private fun String.toByteBuffer(): ByteBuffer = this.toByteArray().let(ByteBuffer::wrap)
}