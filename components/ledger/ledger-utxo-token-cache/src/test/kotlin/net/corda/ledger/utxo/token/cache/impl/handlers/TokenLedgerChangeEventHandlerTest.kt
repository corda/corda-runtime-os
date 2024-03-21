package net.corda.ledger.utxo.token.cache.impl.handlers

import net.corda.ledger.utxo.token.cache.entities.CachedToken
import net.corda.ledger.utxo.token.cache.entities.LedgerChange
import net.corda.ledger.utxo.token.cache.entities.PoolCacheState
import net.corda.ledger.utxo.token.cache.entities.TokenCache
import net.corda.ledger.utxo.token.cache.entities.TokenPoolCache
import net.corda.ledger.utxo.token.cache.handlers.TokenLedgerChangeEventHandler
import net.corda.ledger.utxo.token.cache.impl.POOL_KEY
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class TokenLedgerChangeEventHandlerTest {

    private val tokenCache = mock<TokenCache>()
    private val tokenPoolCache: TokenPoolCache = mock {
        whenever(it.get(any())).thenReturn(tokenCache)
    }
    private val poolCacheState = mock<PoolCacheState>()

    @Test
    fun `produced tokens are not added to the cache`() {
        val token1 = mock<CachedToken>().apply { whenever(stateRef).thenReturn("s1") }

        val ledgerChange = LedgerChange(POOL_KEY, "", "", "", listOf(), listOf(token1))

        val target = TokenLedgerChangeEventHandler()
        val result = target.handle(tokenPoolCache, poolCacheState, ledgerChange)

        assertThat(result).isNull()

        verify(tokenCache, never()).add(any())
    }

    @Test
    fun `consumed tokens are added removed from the cache and claim state`() {
        val token1 = mock<CachedToken>().apply { whenever(stateRef).thenReturn("s1") }
        val token2 = mock<CachedToken>().apply { whenever(stateRef).thenReturn("s2") }

        val ledgerChange = LedgerChange(POOL_KEY, "", "", "", listOf(token1, token2), listOf())

        val target = TokenLedgerChangeEventHandler()
        val result = target.handle(tokenPoolCache, poolCacheState, ledgerChange)

        assertThat(result).isNull()

        verify(tokenCache).removeAll(setOf("s1", "s2"))
        verify(poolCacheState).tokensRemovedFromCache(setOf("s1", "s2"))
    }
}
