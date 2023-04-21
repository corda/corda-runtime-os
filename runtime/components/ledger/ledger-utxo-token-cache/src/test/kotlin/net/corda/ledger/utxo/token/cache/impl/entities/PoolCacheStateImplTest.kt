package net.corda.ledger.utxo.token.cache.impl.entities

import net.corda.data.ledger.utxo.token.selection.data.TokenClaim
import net.corda.data.ledger.utxo.token.selection.state.TokenPoolCacheState
import net.corda.ledger.utxo.token.cache.entities.CachedToken
import net.corda.ledger.utxo.token.cache.entities.PoolCacheStateImpl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class PoolCacheStateImplTest {

    @Test
    fun `is token claimed checks underlying state object`() {
        val claim1 = TokenClaim().apply {
            claimId = "r1"
            claimedTokenStateRefs = listOf("s1", "s2")
        }
        val claim2 = TokenClaim().apply {
            claimId = "r2"
            claimedTokenStateRefs = listOf("s3")
        }

        val state = TokenPoolCacheState().apply {
            this.tokenClaims = listOf(claim1, claim2)
        }

        val target = PoolCacheStateImpl(state)
        assertThat(target.isTokenClaimed("s1")).isTrue
        assertThat(target.isTokenClaimed("s2")).isTrue
        assertThat(target.isTokenClaimed("s3")).isTrue
        assertThat(target.isTokenClaimed("s4")).isFalse()

        // Assert adding/removing a claim is accounted changes the results as expected
        val cachedToken1 = mock<CachedToken>().apply { whenever(stateRef).thenReturn("s4") }
        target.addNewClaim("r3", listOf(cachedToken1))
        assertThat(target.isTokenClaimed("s4")).isTrue
        target.removeClaim("r1")
        assertThat(target.isTokenClaimed("s1")).isFalse
    }

    @Test
    fun `does claim exist checks underlying state object`() {
        val claim1 = TokenClaim().apply {
            claimId = "r1"
            claimedTokenStateRefs = listOf()
        }

        val state = TokenPoolCacheState().apply {
            this.tokenClaims = listOf(claim1)
        }

        val target = PoolCacheStateImpl(state)
        assertThat(target.claimExists("r1")).isTrue
        assertThat(target.claimExists("r2")).isFalse
    }


    @Test
    fun `remove claim removes it from the underlying state object`() {
        val claim1 = TokenClaim().apply {
            claimId = "r1"
            claimedTokenStateRefs = listOf()
        }
        val claim2 = TokenClaim().apply {
            claimId = "r2"
            claimedTokenStateRefs = listOf()
        }

        val state = TokenPoolCacheState().apply {
            this.tokenClaims = listOf(claim1, claim2)
        }

        PoolCacheStateImpl(state).removeClaim("r1")

        assertThat(state.tokenClaims).containsOnly(claim2)
    }

    @Test
    fun `add claim adds it from the underlying state object`() {
        val claim1 = TokenClaim().apply {
            claimId = "r1"
            claimedTokenStateRefs = listOf()
        }

        val cachedToken1 = mock<CachedToken>().apply { whenever(stateRef).thenReturn("s1") }
        val cachedToken2 = mock<CachedToken>().apply { whenever(stateRef).thenReturn("s2") }

        val state = TokenPoolCacheState().apply {
            this.tokenClaims = listOf(claim1)
        }

        PoolCacheStateImpl(state).addNewClaim("r2", listOf(cachedToken1, cachedToken2))

        assertThat(state.tokenClaims).hasSize(2)
        assertThat(state.tokenClaims[1].claimId).isEqualTo("r2")
        assertThat(state.tokenClaims[1].claimedTokenStateRefs).containsOnly("s1", "s2")
    }

    @Test
    fun `when tokens are removed the underlying claims are updated`() {
        val claim1 = TokenClaim().apply {
            claimId = "r1"
            claimedTokenStateRefs = listOf("s1","s2")
        }

        val claim2 = TokenClaim().apply {
            claimId = "r2"
            claimedTokenStateRefs = listOf("s3", "s4")
        }

        val state = TokenPoolCacheState().apply {
            this.tokenClaims = listOf(claim1, claim2)
        }

        PoolCacheStateImpl(state).tokensRemovedFromCache(setOf("s2","s3","s4"))

        // claim1 has s1 left so it should remain, while claim2 has had all it's tokens removed
        // and therefore should also be removed.
        assertThat(state.tokenClaims).hasSize(1)
        assertThat(state.tokenClaims[0].claimId).isEqualTo("r1")
        assertThat(state.tokenClaims[0].claimedTokenStateRefs).containsOnly("s1")
    }

    @Test
    fun `to avro returns underlying state object`() {
        val state = TokenPoolCacheState().apply {
            this.tokenClaims = listOf()
        }

        val result = PoolCacheStateImpl(state).toAvro()

        assertThat(result).isSameAs(state)
    }
}
