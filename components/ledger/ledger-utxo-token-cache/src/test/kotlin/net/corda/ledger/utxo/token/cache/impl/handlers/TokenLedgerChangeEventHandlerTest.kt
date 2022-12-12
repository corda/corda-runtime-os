package net.corda.ledger.utxo.token.cache.impl.handlers

import net.corda.ledger.utxo.token.cache.entities.CachedToken
import net.corda.ledger.utxo.token.cache.entities.LedgerChange
import net.corda.ledger.utxo.token.cache.entities.PoolCacheState
import net.corda.ledger.utxo.token.cache.entities.TokenCache
import net.corda.ledger.utxo.token.cache.handlers.TokenLedgerChangeEventHandler
import net.corda.ledger.utxo.token.cache.impl.POOL_CACHE_KEY
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class TokenLedgerChangeEventHandlerTest {

    private val tokenCache = mock<TokenCache>()
    private val poolCacheState = mock<PoolCacheState>()

    @Test
    fun `produced tokens are added to the cache`() {
        val token1 = mock<CachedToken>().apply { whenever(stateRef).thenReturn("s1") }
        val token2 = mock<CachedToken>().apply { whenever(stateRef).thenReturn("s2") }

        val ledgerChange = LedgerChange(POOL_CACHE_KEY,"","", listOf(), listOf(token1, token2))

        val target = TokenLedgerChangeEventHandler(mock())
        val result = target.handle(tokenCache, poolCacheState, ledgerChange)

        assertThat(result).isNull()

        verify(tokenCache).add(listOf(token1, token2))
    }

    @Test
    fun `consumed tokens are added removed from the cache and claim state`() {
        val token1 = mock<CachedToken>().apply { whenever(stateRef).thenReturn("s1") }
        val token2 = mock<CachedToken>().apply { whenever(stateRef).thenReturn("s2") }

        val ledgerChange = LedgerChange(POOL_CACHE_KEY,"","", listOf(token1, token2), listOf())

        val target = TokenLedgerChangeEventHandler(mock())
        val result = target.handle(tokenCache, poolCacheState, ledgerChange)

        assertThat(result).isNull()

        verify(tokenCache).removeAll(setOf("s1","s2"))
        verify(poolCacheState).tokensRemovedFromCache(setOf("s1","s2"))
    }
}
