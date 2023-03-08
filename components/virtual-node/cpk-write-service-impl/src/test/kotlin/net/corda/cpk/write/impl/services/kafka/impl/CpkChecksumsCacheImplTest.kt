package net.corda.cpk.write.impl.services.kafka.impl

import net.corda.crypto.core.toAvro
import net.corda.data.chunking.Chunk
import net.corda.data.chunking.CpkChunkId
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.v5.crypto.SecureHash
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.nio.ByteBuffer
import java.security.MessageDigest

class CpkChecksumsCacheImplTest {
    private lateinit var cpkChecksumCache: CpkChecksumsCacheImpl
    private lateinit var cacheSynchronizer: CpkChecksumsCacheImpl.CacheSynchronizer
    private lateinit var subscriptionFactory: SubscriptionFactory

    private val subscriptionConfig = SubscriptionConfig("dummyGroupName", "dummyEventTopic")

    companion object {
        fun secureHash(bytes: ByteArray): SecureHash {
            val algorithm = "SHA-256"
            val messageDigest = MessageDigest.getInstance(algorithm)
            return SecureHash(algorithm, messageDigest.digest(bytes))
        }

        fun dummyCpkChunkIdToCpkChunk(cpkChecksum: SecureHash, partNumber: Int, zeroChunk: Boolean) =
            Pair(
                CpkChunkId(cpkChecksum.toAvro(), partNumber),
                Chunk(
                    "dummyRequestId",
                    "dummyFileName",
                    secureHash("dummyChecksum".toByteArray()).toAvro(),
                    partNumber,
                    0,
                    if (zeroChunk)
                        ByteBuffer.wrap(byteArrayOf())
                    else
                        ByteBuffer.wrap(byteArrayOf(0x01, 0x02)),
                    null
                )
            )
    }

    @BeforeEach
    fun setUp() {
        subscriptionFactory = mock()
        cpkChecksumCache = CpkChecksumsCacheImpl(subscriptionFactory, subscriptionConfig, mock())
        cacheSynchronizer = cpkChecksumCache.CacheSynchronizer()
    }

    @Test
    fun `onSnapshot populates cpk checksums cache when meet zero chunk`() {
        val cpkChecksum = secureHash("dummy".toByteArray())
        val pair0 = dummyCpkChunkIdToCpkChunk(cpkChecksum, 0, false)
        val pair1 = dummyCpkChunkIdToCpkChunk(cpkChecksum, 1, true)
        val currentData = mapOf(pair0, pair1)

        cacheSynchronizer.onSnapshot(currentData)
        assertTrue(cpkChecksum in cpkChecksumCache.getCachedCpkIds())
    }

    @Test
    fun `onNext adds the checksum to the cpk checksums cache when meet zero chunk`() {
        val cpkChecksum = secureHash("dummy".toByteArray())
        val pair0 = dummyCpkChunkIdToCpkChunk(cpkChecksum, 0, false)
        val pair1 = dummyCpkChunkIdToCpkChunk(cpkChecksum, 1, true)

        cacheSynchronizer.onNext(Record("dummyTopic", pair0.first, pair0.second), null, mock())
        assertFalse(cpkChecksum in cpkChecksumCache.getCachedCpkIds())

        cacheSynchronizer.onNext(Record("dummyTopic", pair1.first, pair1.second), null, mock())
        assertTrue(cpkChecksum in cpkChecksumCache.getCachedCpkIds())
    }

    @Test
    fun `on partial cpk chunks updates, cpk checksum does not get cached`() {
        val cpkChecksum = secureHash("dummy".toByteArray())
        val pair0 = dummyCpkChunkIdToCpkChunk(cpkChecksum, 0, false)
        val pair1 = dummyCpkChunkIdToCpkChunk(cpkChecksum, 1, false)

        cacheSynchronizer.onNext(Record("dummyTopic", pair0.first, pair0.second), null, mock())
        cacheSynchronizer.onNext(Record("dummyTopic", pair1.first, pair1.second), null, mock())

        assertFalse(cpkChecksum in cpkChecksumCache.getCachedCpkIds())
    }
}