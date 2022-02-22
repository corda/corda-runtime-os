package net.corda.cpk.write.impl.services.kafka.impl

import net.corda.cpk.write.impl.CpkChunk
import net.corda.cpk.write.impl.CpkChunkId
import net.corda.cpk.write.impl.services.kafka.CpkChunkWriter
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

class KafkaCpkChunkWriterTest {
    private lateinit var kafkaCpkChunkWriter: CpkChunkWriter
    private lateinit var publisher: Publisher

    companion object {
        fun secureHash(bytes: ByteArray): SecureHash {
            val algorithm = "SHA-256"
            val messageDigest = MessageDigest.getInstance(algorithm)
            return SecureHash(algorithm, messageDigest.digest(bytes))
        }
    }

    @BeforeEach
    fun setUp() {
        publisher = mock()
        kafkaCpkChunkWriter = KafkaCpkChunkWriter(publisher, 10.seconds)
    }

    @Test
    fun `on putting cpk chunks puts them to Kafka`() {
        val cpkChunkId = CpkChunkId(secureHash("dummy".toByteArray()), 0)
        val cpkChunk = CpkChunk(cpkChunkId, "dummy".toByteArray())
        whenever(publisher.publish(any())).thenReturn(listOf(CompletableFuture<Unit>().also { it.complete(Unit) }))

        kafkaCpkChunkWriter.putAll(listOf(cpkChunk))
    }
}