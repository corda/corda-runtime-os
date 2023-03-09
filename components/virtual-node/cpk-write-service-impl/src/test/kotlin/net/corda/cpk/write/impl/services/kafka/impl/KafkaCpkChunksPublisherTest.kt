package net.corda.cpk.write.impl.services.kafka.impl

import net.corda.crypto.core.toAvro
import net.corda.cpk.write.impl.services.kafka.CpkChunksPublisher
import net.corda.data.chunking.Chunk
import net.corda.data.chunking.CpkChunkId
import net.corda.messaging.api.publisher.Publisher
import net.corda.utilities.seconds
import net.corda.v5.crypto.SecureHash
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.security.MessageDigest
import java.util.concurrent.CompletableFuture

class KafkaCpkChunksPublisherTest {
    private lateinit var kafkaCpkChunksPublisher: CpkChunksPublisher
    private lateinit var publisher: Publisher

    companion object {
        fun secureHash(bytes: ByteArray): net.corda.data.crypto.SecureHash {
            val algorithm = "SHA-256"
            val messageDigest = MessageDigest.getInstance(algorithm)
            return SecureHash(algorithm, messageDigest.digest(bytes)).toAvro()
        }
    }

    @BeforeEach
    fun setUp() {
        publisher = mock()
        kafkaCpkChunksPublisher = KafkaCpkChunksPublisher(publisher, 10.seconds, "dummyTopicName")
    }

    @Test
    fun `on putting cpk chunks puts them to Kafka`() {
        val cpkChunkId = CpkChunkId(secureHash("dummy".toByteArray()), 0)
        val cpkChunk = Chunk()
        whenever(publisher.publish(any())).thenReturn(listOf(CompletableFuture<Unit>().also { it.complete(Unit) }))

        kafkaCpkChunksPublisher.put(cpkChunkId, cpkChunk)
    }
}