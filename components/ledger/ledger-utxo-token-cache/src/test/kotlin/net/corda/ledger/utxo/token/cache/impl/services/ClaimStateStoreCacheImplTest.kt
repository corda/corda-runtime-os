package net.corda.ledger.utxo.token.cache.impl.services

import net.corda.ledger.utxo.token.cache.impl.POOL_KEY
import net.corda.ledger.utxo.token.cache.services.ClaimStateStore
import net.corda.ledger.utxo.token.cache.services.ClaimStateStoreCacheImpl
import net.corda.ledger.utxo.token.cache.services.ClaimStateStoreFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ClaimStateStoreCacheImplTest {
    private val claimStateStoreFactory = mock<ClaimStateStoreFactory>()
    private val claimStateStoreCache = ClaimStateStoreCacheImpl(claimStateStoreFactory)

    @Test
    fun `get when no existing state in store then create default state`() {
        val newClaimStateStore = mock<ClaimStateStore>()
        whenever(claimStateStoreFactory.create(any())).thenReturn(newClaimStateStore)

        val result = claimStateStoreCache.get(POOL_KEY)
        assertThat(result).isEqualTo(newClaimStateStore)

        verify(claimStateStoreFactory).create(eq(POOL_KEY))
    }

    @Test
    fun `get when existing state then load and use`() {
        val newClaimStateStore = mock<ClaimStateStore>()

        whenever(claimStateStoreFactory.create(any())).thenReturn(newClaimStateStore)

        val result = claimStateStoreCache.get(POOL_KEY)
        assertThat(result).isEqualTo(newClaimStateStore)

        verify(claimStateStoreFactory).create(eq(POOL_KEY))
    }

    @Test
    fun `get when existing cached used cached version`() {
        val newClaimStateStore = mock<ClaimStateStore>()
        whenever(claimStateStoreFactory.create(any())).thenReturn(newClaimStateStore)

        val result1 = claimStateStoreCache.get(POOL_KEY)
        assertThat(result1).isEqualTo(newClaimStateStore)

        val result2 = claimStateStoreCache.get(POOL_KEY)
        verify(claimStateStoreFactory, times(1)).create(any())
        assertThat(result2).isSameAs(result1)
        verify(claimStateStoreFactory, times(1)).create(any())
    }
}
