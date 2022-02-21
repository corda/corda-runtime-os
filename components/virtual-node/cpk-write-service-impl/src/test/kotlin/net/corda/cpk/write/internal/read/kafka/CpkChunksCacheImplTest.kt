package net.corda.cpk.write.internal.read.kafka

import net.corda.cpk.write.internal.read.AvroTypesTodo
import net.corda.cpk.write.internal.read.toAvro
import net.corda.cpk.write.internal.read.toCorda
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.v5.crypto.SecureHash
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.security.MessageDigest

class CpkChunksCacheImplTest {
    private lateinit var cpkChecksumCache: CpkChunksCacheImpl
    private lateinit var cacheSynchronizer: CpkChunksCacheImpl.CacheSynchronizer
    private lateinit var subscriptionFactory: SubscriptionFactory

    private val subscriptionConfig = SubscriptionConfig("dummyGroupName", "dummyEventTopic")
    private val cpkChunkAvro = AvroTypesTodo.CpkChunkAvro(mock(), byteArrayOf())

    companion object {
        fun secureHash(bytes: ByteArray): net.corda.data.crypto.SecureHash {
            val algorithm = "SHA-256"
            val messageDigest = MessageDigest.getInstance(algorithm)
            return SecureHash(algorithm, messageDigest.digest(bytes)).toAvro()
        }
    }

    @BeforeEach
    fun setUp() {
        subscriptionFactory = mock()
        cpkChecksumCache = CpkChunksCacheImpl(subscriptionFactory, subscriptionConfig, mock())
        cacheSynchronizer = cpkChecksumCache.CacheSynchronizer()
    }

    @Test
    fun `onSnapshot populates cpk chunk ids map with all data`() {
        val cpkChecksum = secureHash("dummy".toByteArray())
        val cpkChunkIdAvro0 = AvroTypesTodo.CpkChunkIdAvro(cpkChecksum, 0)
        val cpkChunkIdAvro1 = AvroTypesTodo.CpkChunkIdAvro(cpkChecksum, 1)
        val pair0 = cpkChunkIdAvro0 to cpkChunkAvro
        val pair1 = cpkChunkIdAvro1 to cpkChunkAvro
        val currentData = mapOf(pair0, pair1)

        cacheSynchronizer.onSnapshot(currentData)
        assertThat(
            mapOf(
                cpkChunkIdAvro0.toCorda() to cpkChunkIdAvro0.toCorda(),
                cpkChunkIdAvro1.toCorda() to cpkChunkIdAvro1.toCorda()
            )
        ).isEqualTo(cpkChecksumCache.cpkChunkIds)
    }

    @Test
    fun `onNext adds cpk checksum if it does not already exist`() {
        val cpkChecksum = secureHash("dummy".toByteArray())
        val cpkChunkIdAvro0 = AvroTypesTodo.CpkChunkIdAvro(cpkChecksum, 0)
        val pair0 =  cpkChunkIdAvro0 to cpkChunkAvro

        cacheSynchronizer.onNext(
            Record("dummyTopic", pair0.first, pair0.second),
            null,
            mock()
        )
        assertThat(mapOf(cpkChunkIdAvro0.toCorda() to cpkChunkIdAvro0.toCorda())).isEqualTo(cpkChecksumCache.cpkChunkIds)

        // already exists so shouldn't be re-added
        cacheSynchronizer.onNext(
            Record("dummyTopic", pair0.first, pair0.second),
            null,
            mock()
        )
        assertThat(mapOf(cpkChunkIdAvro0.toCorda() to cpkChunkIdAvro0.toCorda())).isEqualTo(cpkChecksumCache.cpkChunkIds)
    }
}