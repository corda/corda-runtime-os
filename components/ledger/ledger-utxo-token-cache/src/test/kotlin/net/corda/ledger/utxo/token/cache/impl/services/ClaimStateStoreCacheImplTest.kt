package net.corda.ledger.utxo.token.cache.impl.services

import net.corda.ledger.utxo.token.cache.impl.POOL_KEY
import net.corda.ledger.utxo.token.cache.impl.TOKEN_POOL_CACHE_STATE
import net.corda.ledger.utxo.token.cache.services.StoredPoolClaimState
import net.corda.ledger.utxo.token.cache.services.ClaimStateStore
import net.corda.ledger.utxo.token.cache.services.ClaimStateStoreCacheImpl
import net.corda.ledger.utxo.token.cache.services.ClaimStateStoreFactory
import net.corda.ledger.utxo.token.cache.services.TokenPoolCacheStateSerialization
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.utilities.time.Clock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

class ClaimStateStoreCacheImplTest {
    private val now = Instant.ofEpochMilli(1)
    private val stateManager = mock<StateManager>()
    private val serialization = mock<TokenPoolCacheStateSerialization>()
    private val claimStateStoreFactory = mock<ClaimStateStoreFactory>()
    private val clock = mock<Clock>().apply { whenever(instant()).thenReturn(now) }

    private val target = ClaimStateStoreCacheImpl(stateManager, serialization, claimStateStoreFactory, clock)

    @Test
    fun `get when no existing state in store then create default state`() {
        val newClaimStateStore = mock<ClaimStateStore>()
        val stateBytes = "data".toByteArray()
        whenever(stateManager.get(any())).thenReturn(mapOf())
        whenever(claimStateStoreFactory.create(any(), any())).thenReturn(newClaimStateStore)
        whenever(serialization.serialize(any())).thenReturn(stateBytes)

        val expectedState = TOKEN_POOL_CACHE_STATE
        val expectedStoredState = State(POOL_KEY.toString(), stateBytes, modifiedTime = now)

        val expectedStoredPoolClaimState = StoredPoolClaimState(
            State.VERSION_INITIAL_VALUE,
            POOL_KEY,
            expectedState
        )

        val result = target.get(POOL_KEY)
        assertThat(result).isEqualTo(newClaimStateStore)

        verify(claimStateStoreFactory).create(eq(POOL_KEY), eq(expectedStoredPoolClaimState))
        verify(stateManager).create(eq(listOf(expectedStoredState)))
    }

    @Test
    fun `get when existing state then load and use`() {
        val newClaimStateStore = mock<ClaimStateStore>()
        val stateBytes = "data".toByteArray()
        val stateRecord = State(POOL_KEY.toString(), stateBytes, 1)
        val expectedState = TOKEN_POOL_CACHE_STATE

        whenever(stateManager.get(any())).thenReturn(mapOf(POOL_KEY.toString() to stateRecord))
        whenever(serialization.deserialize(any())).thenReturn(expectedState)
        whenever(claimStateStoreFactory.create(any(), any())).thenReturn(newClaimStateStore)

        val expectedStoredPoolClaimState = StoredPoolClaimState(
            stateRecord.version,
            POOL_KEY,
            expectedState
        )

        val result = target.get(POOL_KEY)
        assertThat(result).isEqualTo(newClaimStateStore)

        verify(serialization).deserialize(stateBytes)
        verify(claimStateStoreFactory).create(eq(POOL_KEY), eq(expectedStoredPoolClaimState))
    }

    @Test
    fun `get when existing cached used cached version`() {
        val newClaimStateStore = mock<ClaimStateStore>()
        val stateBytes = "data".toByteArray()
        whenever(stateManager.get(any())).thenReturn(mapOf())
        whenever(serialization.serialize(any())).thenReturn(stateBytes)
        whenever(claimStateStoreFactory.create(any(), any())).thenReturn(newClaimStateStore)

        val result1 = target.get(POOL_KEY)
        assertThat(result1).isEqualTo(newClaimStateStore)

        val result2 = target.get(POOL_KEY)
        verify(claimStateStoreFactory, times(1)).create(any(), any())
        assertThat(result2).isSameAs(result1)
        verify(claimStateStoreFactory, times(1)).create(any(), any())
    }
}