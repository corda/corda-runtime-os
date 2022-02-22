package net.corda.cpk.write.impl.services.kafka.impl

import net.corda.cpk.write.impl.services.kafka.AvroTypesTodo
import net.corda.cpk.write.impl.services.kafka.CpkChunksWriter
import net.corda.cpk.write.impl.services.kafka.toAvro
import net.corda.data.chunking.Chunk
import net.corda.messaging.api.publisher.Publisher
import net.corda.v5.base.util.seconds
import net.corda.v5.crypto.SecureHash
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.security.MessageDigest
import java.util.concurrent.CompletableFuture

class KafkaCpkChunksWriterTest {
    private lateinit var kafkaCpkChunksWriter: CpkChunksWriter
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
        kafkaCpkChunksWriter = KafkaCpkChunksWriter(publisher, 10.seconds)
    }

    @Test
    fun `on putting cpk chunks puts them to Kafka`() {
        val cpkChunkId = AvroTypesTodo.CpkChunkIdAvro(secureHash("dummy".toByteArray()), 0)
        val cpkChunk = Chunk()
        whenever(publisher.publish(any())).thenReturn(listOf(CompletableFuture<Unit>().also { it.complete(Unit) }))

        kafkaCpkChunksWriter.putAll(listOf(cpkChunkId to cpkChunk))
    }
}