package net.corda.ledger.utxo.token.cache.impl.services

import net.corda.crypto.core.SecureHashImpl
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.ledger.utxo.token.cache.entities.CachedToken
import net.corda.ledger.utxo.token.cache.entities.TokenPoolKey
import net.corda.ledger.utxo.token.cache.entities.internal.TokenCacheImpl
import net.corda.ledger.utxo.token.cache.repositories.UtxoTokenRepository
import net.corda.ledger.utxo.token.cache.services.TokenSelectionMetricsImpl
import net.corda.ledger.utxo.token.cache.services.internal.AvailableTokenServiceImpl
import net.corda.ledger.utxo.token.cache.services.internal.BackoffManager
import net.corda.orm.JpaEntitiesRegistry
import net.corda.orm.JpaEntitiesSet
import net.corda.test.util.time.AutoTickTestClock
import net.corda.utilities.seconds
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory

class BackoffManagerTest {

    private val tokenPoolKey1 = TokenPoolKey("shid1", "tt", "ih", "not", "sym")
    private val tokenPoolKey2 = TokenPoolKey("shid2", "tt", "ih", "not", "sym")

    @Test
    fun `don't backoff if key is not present`() {
        val backoffManager = BackoffManager(AutoTickTestClock(Instant.EPOCH, 1.seconds), 500L, 1L)
        assertThat(backoffManager.backoff(tokenPoolKey1)).isFalse()
    }

    @Test
    fun `Backoff if key is present and backoff period has not expired`() {
        val backoffManager = BackoffManager(
            AutoTickTestClock(Instant.EPOCH, 1.seconds),
            2000L,
            2000L
        )

        backoffManager.update(tokenPoolKey1)
        assertThat(backoffManager.backoff(tokenPoolKey1)).isTrue()
    }

    @Test
    fun `ensure the max interval is respected`() {
        val backoffManager = BackoffManager(
            AutoTickTestClock(Instant.EPOCH, 1.seconds),
            1000L,
            4000L
        )

        backoffManager.update(tokenPoolKey1) // backoff period - 1 second
        backoffManager.update(tokenPoolKey1) // backoff period - 2 seconds
        backoffManager.update(tokenPoolKey1) // backoff period - 4 seconds
        backoffManager.update(tokenPoolKey1) // backoff period - 5 seconds

        assertThat(backoffManager.backoff(tokenPoolKey1)).isTrue() // backoff time has not expired
        assertThat(backoffManager.backoff(tokenPoolKey1)).isTrue() // backoff time has not expired
        assertThat(backoffManager.backoff(tokenPoolKey1)).isTrue() // backoff time has not expired
        assertThat(backoffManager.backoff(tokenPoolKey1)).isTrue() // backoff time has not expired
        assertThat(backoffManager.backoff(tokenPoolKey1)).isFalse() // backoff time has expired
    }

    @Test
    fun `ensure entry is removed after max interval is reached`() {
        val backoffManager = BackoffManager(
            AutoTickTestClock(Instant.EPOCH, 10.seconds),
            0L,
            0L // expire immediately
        )

        backoffManager.update(tokenPoolKey1)
        assertThat(backoffManager.backoff(tokenPoolKey1)).isFalse() // Key has been removed
    }

    @Test
    fun `ensure each token pool key is managed independently`() {

        val backoffManager = BackoffManager(
            AutoTickTestClock(Instant.EPOCH, 1.seconds),
            2000L,
            10000L
        )

        backoffManager.update(tokenPoolKey1)
        assertThat(backoffManager.backoff(tokenPoolKey1)).isTrue() // backoff time has not expired
        backoffManager.update(tokenPoolKey2)
        assertThat(backoffManager.backoff(tokenPoolKey1)).isFalse() // backoff time has expired
        assertThat(backoffManager.backoff(tokenPoolKey2)).isTrue()  // backoff time has not expired
        assertThat(backoffManager.backoff(tokenPoolKey2)).isFalse() // backoff time has expired
    }
}
