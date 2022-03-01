package net.corda.introspiciere.junit

import net.corda.introspiciere.domain.TopicDefinition
import net.corda.p2p.test.KeyAlgorithm
import net.corda.p2p.test.KeyPairEntry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.nio.ByteBuffer
import java.security.KeyPairGenerator

class InMemoryIntrospiciereServerTest {

    companion object {
        @RegisterExtension
        @JvmStatic
        private val introspiciere = InMemoryIntrospiciereServer(
            // This only works locally at the moment. For CI it should read
            // this for an environment variable or from a config file
            // kafkaBrokers = getMinikubeKafkaBroker()
            kafkaBrokers = "20.62.51.171:9094"
        )
    }

    @Test
    fun `I can start the server as an extension`() {
        val topic = "topic".random8
        val key = "key".random8
        val keyPairEntry = generateKeyPairEntry()

        introspiciere.client.createTopic(topic, 3)

        val seq = introspiciere.client.readFromLatest<KeyPairEntry>(topic, key)
        val iter = seq.iterator()

        introspiciere.client.write(topic, key, keyPairEntry)

        assertEquals(keyPairEntry, iter.next())
        assertNull(iter.next())
    }

    private fun generateKeyPairEntry(): KeyPairEntry {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(571)
        val pair = generator.generateKeyPair()
        return KeyPairEntry(
            KeyAlgorithm.ECDSA,
            ByteBuffer.wrap(pair.public.encoded),
            ByteBuffer.wrap(pair.private.encoded)
        )
    }
}

