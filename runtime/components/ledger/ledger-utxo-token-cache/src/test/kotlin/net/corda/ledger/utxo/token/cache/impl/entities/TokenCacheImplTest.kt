package net.corda.ledger.utxo.token.cache.impl.entities

import net.corda.data.ledger.utxo.token.selection.data.Token
import net.corda.data.ledger.utxo.token.selection.state.TokenPoolCacheState
import net.corda.ledger.utxo.token.cache.converters.EntityConverter
import net.corda.ledger.utxo.token.cache.entities.CachedToken
import net.corda.ledger.utxo.token.cache.entities.TokenCacheImpl
import net.corda.ledger.utxo.token.cache.impl.POOL_CACHE_KEY
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class TokenCacheImplTest {

    private val entityConverter = mock<EntityConverter>()
    private val tokenPoolCacheState = TokenPoolCacheState().apply {
        this.poolKey = POOL_CACHE_KEY
        this.availableTokens = listOf()
        this.tokenClaims = listOf()
    }
    private val target = TokenCacheImpl(tokenPoolCacheState, entityConverter)

    @Test
    fun `adding a new token updates the underlying state object`() {
        val cachedToken = mock<CachedToken>()
        val token = Token()

        whenever(cachedToken.toAvro()).thenReturn(token)

        target.add(listOf(cachedToken))

        assertThat(tokenPoolCacheState.availableTokens).containsOnly(token)
    }

    @Test
    fun `adding a new token replaces any existing token for the same state ref, on the underlying state object`() {
        val token1 = Token().apply { stateRef = "s1" }
        val token2 = Token().apply { stateRef = "s1" }
        val cachedToken2 = mock<CachedToken>().apply { whenever(stateRef).thenReturn("s1") }
        tokenPoolCacheState.availableTokens = listOf(token1)

        whenever(cachedToken2.toAvro()).thenReturn(token2)

        assertThat(tokenPoolCacheState.availableTokens).containsOnly(token1)

        target.add(listOf(cachedToken2))

        assertThat(tokenPoolCacheState.availableTokens).containsOnly(token2)
    }

    @Test
    fun `removing a set of state refs removes the tokens from the underlying state object`() {
        val token1 = Token().apply { stateRef = "s1" }
        val token2 = Token().apply { stateRef = "s2" }
        val token3 = Token().apply { stateRef = "s3" }
        val token4 = Token().apply { stateRef = "s4" }

        tokenPoolCacheState.availableTokens = listOf(token1, token2, token3, token4)
        target.removeAll(setOf("s2", "s4"))

        assertThat(tokenPoolCacheState.availableTokens).containsOnly(token1, token3)
    }

    @Test
    fun `iterating the cache returns all tokens from the underlying state object`() {
        val token1 = Token().apply { stateRef = "s1" }
        val token2 = Token().apply { stateRef = "s2" }

        tokenPoolCacheState.availableTokens = listOf(token1, token2)

        val cachedToken1 = mock<CachedToken>()
        val cachedToken2 = mock<CachedToken>()

        whenever(entityConverter.toCachedToken(token1)).thenReturn(cachedToken1)
        whenever(entityConverter.toCachedToken(token2)).thenReturn(cachedToken2)

        assertThat(target.toList()).containsOnly(cachedToken1, cachedToken2)
    }
}
