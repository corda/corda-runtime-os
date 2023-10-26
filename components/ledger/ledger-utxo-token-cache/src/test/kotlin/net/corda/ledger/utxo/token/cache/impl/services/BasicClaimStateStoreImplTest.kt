package net.corda.ledger.utxo.token.cache.impl.services

import net.corda.ledger.utxo.token.cache.impl.POOL_KEY
import net.corda.ledger.utxo.token.cache.impl.TOKEN_POOL_CACHE_STATE
import net.corda.ledger.utxo.token.cache.impl.TOKEN_POOL_CACHE_STATE_2
import net.corda.ledger.utxo.token.cache.impl.getUniqueTokenPoolCacheState
import net.corda.ledger.utxo.token.cache.services.BasicClaimStateStoreImpl
import net.corda.ledger.utxo.token.cache.services.StoredPoolClaimState
import net.corda.ledger.utxo.token.cache.services.TokenPoolCacheStateSerialization
import net.corda.libs.statemanager.api.Metadata
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.utilities.time.Clock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

class BasicClaimStateStoreImplTest {
    private val serialization = mock<TokenPoolCacheStateSerialization>()
    private val stateManager = mock<StateManager>()
    private val now = Instant.ofEpochMilli(1)
    private val clock = mock<Clock>().apply { whenever(instant()).thenReturn(now) }

    @Test
    fun `success updates claim state`() {
        val storedPoolClaimState = StoredPoolClaimState(1, POOL_KEY, TOKEN_POOL_CACHE_STATE)
        val stateBytes = "data".toByteArray()
        val expectedStateManagerState = State(POOL_KEY.toString(), stateBytes, 1, Metadata(), now)

        whenever(serialization.serialize(TOKEN_POOL_CACHE_STATE_2)).thenReturn(stateBytes)
        whenever(stateManager.update(any())).thenReturn(mapOf())

        val target = createTarget(storedPoolClaimState)

        val result = target.enqueueRequest { currentPoolState ->
            assertThat(currentPoolState).isEqualTo(TOKEN_POOL_CACHE_STATE)
            TOKEN_POOL_CACHE_STATE_2
        }

        assertThat(result.get()).isTrue
        verify(stateManager).update(eq(listOf(expectedStateManagerState)))
    }

    @Test
    fun `concurrency failure sets state to stored version`() {
        /*
        When a request to update state fails due to a concurrency check
        then the current state should be restored to the stored state
        then when we attempt to update state again the stored state is used
         */
        val initialPoolState = getUniqueTokenPoolCacheState(POOL_KEY)
        val storedPoolState = getUniqueTokenPoolCacheState(POOL_KEY)
        val attemptedUpdatePoolState = getUniqueTokenPoolCacheState(POOL_KEY)
        val storedPoolClaimState = StoredPoolClaimState(1, POOL_KEY, initialPoolState)
        val initialPoolStateBytes = "data1".toByteArray()
        val stateBytes2 = "data2".toByteArray()
        val storedPoolStateBytes = "store".toByteArray()
        val storedState = State(POOL_KEY.toString(), storedPoolStateBytes, 4, Metadata(), now)

        whenever(serialization.serialize(initialPoolState)).thenReturn(initialPoolStateBytes)
        whenever(serialization.deserialize(storedPoolStateBytes)).thenReturn(storedPoolState)
        whenever(serialization.serialize(attemptedUpdatePoolState)).thenReturn(stateBytes2)

        whenever(stateManager.update(any())).thenReturn(mapOf(POOL_KEY.toString() to storedState))

        val target = createTarget(storedPoolClaimState)

        val result1 = target.enqueueRequest { currentPoolState ->
            assertThat(currentPoolState).isEqualTo(initialPoolState)
            attemptedUpdatePoolState
        }
        assertThat(result1.get()).isFalse

        whenever(stateManager.update(any())).thenReturn(mapOf())
        val result2 = target.enqueueRequest { currentPoolState ->
            assertThat(currentPoolState).isEqualTo(storedPoolState)
            attemptedUpdatePoolState
        }

        assertThat(result2.get()).isTrue
    }

    private fun createTarget(storedPoolClaimState: StoredPoolClaimState): BasicClaimStateStoreImpl {
        return BasicClaimStateStoreImpl(POOL_KEY, storedPoolClaimState, serialization, stateManager, clock)
    }
}