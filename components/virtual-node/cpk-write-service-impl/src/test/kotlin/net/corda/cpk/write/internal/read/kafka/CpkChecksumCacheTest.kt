package net.corda.cpk.write.internal.read.kafka

import net.corda.cpk.write.CpkInfo
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.v5.crypto.SecureHash
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.security.MessageDigest

class CpkChecksumCacheTest {
    private lateinit var cpkChecksumCache: CpkChecksumCache
    private lateinit var cacheSynchronizer: CpkChecksumCache.CacheSynchronizer
    private lateinit var subscriptionFactory: SubscriptionFactory

    private val subscriptionConfig = SubscriptionConfig("dummy", "dummy")
    private val cpkInfo = CpkInfo(
        secureHash("dummy".toByteArray()),
        "dummy",
        "dummy",
        secureHash("dummy".toByteArray())
    )

    @BeforeEach
    fun setUp() {
        subscriptionFactory = mock()
        cpkChecksumCache = CpkChecksumCache(subscriptionConfig, subscriptionFactory)
        cacheSynchronizer = cpkChecksumCache.CacheSynchronizer()
    }

    @Test
    fun `onSnapshot populates cpk checksums map with all data`() {
        val secureHash0 = secureHash("dummy0".toByteArray())
        val secureHash1 = secureHash("dummy1".toByteArray())
        val pair0 =  secureHash0 to cpkInfo
        val pair1 = secureHash1 to cpkInfo
        val currentData = mapOf(pair0, pair1)

        cacheSynchronizer.onSnapshot(currentData)
        assertThat(mapOf(secureHash0 to secureHash0, secureHash1 to secureHash1)).isEqualTo(cpkChecksumCache.cpkChecksums)
    }

    @Test
    fun `onNext adds cpk checksum if it does not already exist`() {
        val secureHash0 = secureHash("dummy0".toByteArray())
        val pair0 =  secureHash0 to cpkInfo

        cacheSynchronizer.onNext(
            Record("dummyTopic", pair0.first, pair0.second),
            null,
            mock()
        )
        assertThat(mapOf(secureHash0 to secureHash0)).isEqualTo(cpkChecksumCache.cpkChecksums)

        // already exists so shouldn't be re-added
        cacheSynchronizer.onNext(
            Record("dummyTopic", pair0.first, pair0.second),
            null,
            mock()
        )
        assertThat(mapOf(secureHash0 to secureHash0)).isEqualTo(cpkChecksumCache.cpkChecksums)
    }


    private fun secureHash(bytes: ByteArray): SecureHash {
        val algorithm = "SHA-256"
        val messageDigest = MessageDigest.getInstance(algorithm)
        return SecureHash(algorithm, messageDigest.digest(bytes))
    }
}