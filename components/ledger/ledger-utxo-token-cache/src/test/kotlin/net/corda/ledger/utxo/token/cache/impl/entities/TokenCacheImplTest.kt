package net.corda.ledger.utxo.token.cache.impl.entities

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
    private val target = TokenCacheImpl()

    @Test
    fun `adding a token`() {
        val cachedToken = mock<CachedToken>()
        target.add(listOf(cachedToken))
        assertThat(target.toList()).containsOnly(cachedToken)
    }

    @Test
    fun `removing a token`() {
        val cachedToken1 = mock<CachedToken>().apply { whenever(stateRef).thenReturn("s1") }
        val cachedToken2 = mock<CachedToken>().apply { whenever(stateRef).thenReturn("s2") }
        val cachedToken3 = mock<CachedToken>().apply { whenever(stateRef).thenReturn("s3") }
        target.add(listOf(cachedToken1, cachedToken2, cachedToken3))

        assertThat(target.toList()).containsOnly(cachedToken1, cachedToken2, cachedToken3)
        target.removeAll(setOf("s1", "s3"))
        assertThat(target.toList()).containsOnly(cachedToken2)
    }
}
