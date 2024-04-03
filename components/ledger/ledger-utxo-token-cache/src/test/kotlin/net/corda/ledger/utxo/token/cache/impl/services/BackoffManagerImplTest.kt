package net.corda.ledger.utxo.token.cache.impl.services

import net.corda.ledger.utxo.token.cache.entities.TokenPoolKey
import net.corda.ledger.utxo.token.cache.services.internal.BackoffManagerImpl
import net.corda.test.util.time.AutoTickTestClock
import net.corda.utilities.seconds
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class BackoffManagerImplTest {

    private val tokenPoolKey1 = TokenPoolKey("shid1", "tt", "ih", "not", "sym")
    private val tokenPoolKey2 = TokenPoolKey("shid2", "tt", "ih", "not", "sym")

    @Test
    fun `don't backoff if key is not present`() {
        val backoffManager = BackoffManagerImpl(AutoTickTestClock(Instant.EPOCH, 1.seconds), 500L, 1L)
        assertThat(backoffManager.backoff(tokenPoolKey1)).isFalse()
    }

    @Test
    fun `Backoff if key is present and backoff period has not expired`() {
        val backoffManager = BackoffManagerImpl(
            AutoTickTestClock(Instant.EPOCH, 1.seconds),
            2000L,
            2000L
        )

        backoffManager.update(tokenPoolKey1)
        assertThat(backoffManager.backoff(tokenPoolKey1)).isTrue()
    }

    @Test
    fun `ensure the max interval is respected`() {
        val backoffManager = BackoffManagerImpl(
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
        val backoffManager = BackoffManagerImpl(
            AutoTickTestClock(Instant.EPOCH, 10.seconds),
            0L,
            0L // expire immediately
        )

        backoffManager.update(tokenPoolKey1)
        assertThat(backoffManager.backoff(tokenPoolKey1)).isFalse() // Key has been removed
    }

    @Test
    fun `ensure each token pool key is managed independently`() {

        val backoffManager = BackoffManagerImpl(
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
