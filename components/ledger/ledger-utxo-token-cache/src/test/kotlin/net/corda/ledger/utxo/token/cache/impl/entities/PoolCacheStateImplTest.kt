package net.corda.ledger.utxo.token.cache.impl.entities

import net.corda.data.ledger.utxo.token.selection.data.Token
import net.corda.data.ledger.utxo.token.selection.data.TokenAmount
import net.corda.data.ledger.utxo.token.selection.data.TokenClaim
import net.corda.data.ledger.utxo.token.selection.key.TokenPoolCacheKey
import net.corda.data.ledger.utxo.token.selection.state.TokenPoolCacheState
import net.corda.ledger.utxo.token.cache.converters.EntityConverter
import net.corda.ledger.utxo.token.cache.entities.CachedToken
import net.corda.ledger.utxo.token.cache.entities.internal.PoolCacheStateImpl
import net.corda.ledger.utxo.token.cache.services.ServiceConfiguration
import net.corda.utilities.time.Clock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant

class PoolCacheStateImplTest {

    private val serviceConfiguration = mock<ServiceConfiguration>()
    private val entityConverter = mock<EntityConverter>()
    private val clock = mock<Clock>()
    private val poolKey = TokenPoolCacheKey.newBuilder()
        .setShortHolderId("h")
        .setTokenType("t")
        .setIssuerHash("i")
        .setNotaryX500Name("n")
        .setSymbol("s")
        .build()

    @Test
    fun `is token claimed checks underlying state object`() {
        val claim1 = TokenClaim().apply {
            claimId = "r1"
            claimedTokens = listOf(createToken("s1"), createToken("s2"))
        }
        val claim2 = TokenClaim().apply {
            claimId = "r2"
            claimedTokens = listOf(createToken("s3"))
        }

        val state = TokenPoolCacheState().apply {
            this.tokenClaims = listOf(claim1, claim2)
        }

        val target = createPoolCacheStateImpl(state)
        assertThat(target.isTokenClaimed("s1")).isTrue
        assertThat(target.isTokenClaimed("s2")).isTrue
        assertThat(target.isTokenClaimed("s3")).isTrue
        assertThat(target.isTokenClaimed("s4")).isFalse()

        // Assert adding/removing a claim is accounted changes the results as expected
        val cachedToken1 = mock<CachedToken>().apply {
            whenever(stateRef).thenReturn("s4")
            whenever(toAvro()).thenReturn(createToken("s4"))
        }
        target.addNewClaim("r3", listOf(cachedToken1))
        assertThat(target.isTokenClaimed("s4")).isTrue
        target.removeClaim("r1")
        assertThat(target.isTokenClaimed("s1")).isFalse
    }

    @Test
    fun `does claim exist checks underlying state object`() {
        val claim1 = TokenClaim().apply {
            claimId = "r1"
            claimedTokens = listOf()
        }

        val state = TokenPoolCacheState().apply {
            this.tokenClaims = listOf(claim1)
        }

        val target = createPoolCacheStateImpl(state)
        assertThat(target.claimExists("r1")).isTrue
        assertThat(target.claimExists("r2")).isFalse
    }

    @Test
    fun `remove claim removes it from the underlying state object`() {
        val claim1 = TokenClaim().apply {
            claimId = "r1"
            claimedTokens = listOf()
        }
        val claim2 = TokenClaim().apply {
            claimId = "r2"
            claimedTokens = listOf()
        }

        val state = TokenPoolCacheState().apply {
            this.tokenClaims = listOf(claim1, claim2)
        }

        createPoolCacheStateImpl(state).removeClaim("r1")

        assertThat(state.tokenClaims).containsOnly(claim2)
    }

    @Test
    fun `add claim adds it from the underlying state object`() {
        val claim1 = TokenClaim().apply {
            claimId = "r1"
            claimedTokens = listOf()
        }

        val cachedToken1 = mock<CachedToken>().apply {
            whenever(stateRef).thenReturn("s1")
            whenever(toAvro()).thenReturn(createToken("s1"))
        }
        val cachedToken2 = mock<CachedToken>().apply {
            whenever(stateRef).thenReturn("s2")
            whenever(toAvro()).thenReturn(createToken("s2"))
        }

        val state = TokenPoolCacheState().apply {
            this.tokenClaims = listOf(claim1)
        }

        createPoolCacheStateImpl(state).addNewClaim("r2", listOf(cachedToken1, cachedToken2))

        assertThat(state.tokenClaims).hasSize(2)
        assertThat(state.tokenClaims[1].claimId).isEqualTo("r2")
        assertThat(state.tokenClaims[1].claimedTokens).containsOnly(createToken("s1"), createToken("s2"))
    }

    @Test
    fun `when tokens are removed the underlying claims are updated`() {
        val claim1 = TokenClaim().apply {
            claimId = "r1"
            claimedTokens = listOf(createToken("s1"), createToken("s2"))
        }

        val claim2 = TokenClaim().apply {
            claimId = "r2"
            claimedTokens = listOf(createToken("s3"), createToken("s4"))
        }

        val state = TokenPoolCacheState().apply {
            this.tokenClaims = listOf(claim1, claim2)
        }

        createPoolCacheStateImpl(state).tokensRemovedFromCache(setOf("s2", "s3", "s4"))

        // claim1 has s1 left so it should remain, while claim2 has had all it's tokens removed
        // and therefore should also be removed.
        assertThat(state.tokenClaims).hasSize(1)
        assertThat(state.tokenClaims[0].claimId).isEqualTo("r1")
        assertThat(state.tokenClaims[0].claimedTokens).containsOnly(createToken("s1"))
    }

    @Test
    fun `to avro returns underlying state object`() {
        val state = TokenPoolCacheState().apply {
            this.tokenClaims = listOf()
        }

        val result = createPoolCacheStateImpl(state).toAvro()

        assertThat(result).isSameAs(state)
    }

    @Test
    fun `remove expired tokens updates 5_0 state with a timestamp`() {
        val claim1 = TokenClaim.newBuilder()
            .setClaimId("1")
            .setClaimTimestamp(null)
            .setClaimedTokens(listOf())
            .build()

        val poolState = TokenPoolCacheState.newBuilder()
            .setPoolKey(poolKey)
            .setTokenClaims(mutableListOf(claim1))
            .setAvailableTokens(mutableListOf())
            .build()

        whenever(serviceConfiguration.claimTimeoutSeconds).thenReturn(1)
        whenever(clock.instant()).thenReturn(Instant.ofEpochMilli(1000))

        val target = createPoolCacheStateImpl(poolState)

        target.removeExpiredClaims()

        assertThat(target.toAvro().tokenClaims[0].claimTimestamp).isEqualTo(1000)
    }

    @Test
    fun `remove expired tokens removes claims that have breached the timeout`() {
        val claim1 = TokenClaim.newBuilder()
            .setClaimId("1")
            .setClaimTimestamp(null)
            .setClaimedTokens(listOf())
            .build()
        val claim2 = TokenClaim.newBuilder()
            .setClaimId("2")
            .setClaimTimestamp(1)
            .setClaimedTokens(listOf())
            .build()

        val poolState = TokenPoolCacheState.newBuilder()
            .setPoolKey(poolKey)
            .setTokenClaims(mutableListOf(claim1, claim2))
            .setAvailableTokens(mutableListOf())
            .build()

        whenever(serviceConfiguration.claimTimeoutSeconds).thenReturn(1)
        whenever(clock.instant()).thenReturn(Instant.ofEpochMilli(999))

        val target = createPoolCacheStateImpl(poolState)
        // Should not do anything yet
        target.removeExpiredClaims()
        assertThat(target.claimExists("1")).isTrue
        assertThat(target.claimExists("2")).isTrue

        whenever(clock.instant()).thenReturn(Instant.ofEpochMilli(1002))
        target.removeExpiredClaims()
        assertThat(target.claimExists("1")).isTrue
        assertThat(target.claimExists("2")).isFalse
    }

    @Test
    fun `remove invalid claims`() {
        val claim1 = TokenClaim.newBuilder()
            .setClaimId("1")
            .setClaimTimestamp(null)
            .setClaimedTokens(listOf())
            .build()
        val claim2 = TokenClaim.newBuilder()
            .setClaimId("2")
            .setClaimTimestamp(1)
            .setClaimedTokens(listOf())
            .setClaimedTokenStateRefs(listOf("ref1"))
            .build()

        val poolState = TokenPoolCacheState.newBuilder()
            .setPoolKey(poolKey)
            .setTokenClaims(mutableListOf(claim1, claim2))
            .setAvailableTokens(mutableListOf())
            .build()

        whenever(serviceConfiguration.claimTimeoutSeconds).thenReturn(1)
        whenever(clock.instant()).thenReturn(Instant.ofEpochMilli(999))

        val target = createPoolCacheStateImpl(poolState)
        target.removeInvalidClaims()
        assertThat(target.claimExists("1")).isTrue
        assertThat(target.claimExists("2")).isFalse
    }

    private fun createPoolCacheStateImpl(cacheState: TokenPoolCacheState): PoolCacheStateImpl {
        return PoolCacheStateImpl(cacheState, serviceConfiguration, entityConverter, clock)
    }

    private fun createToken(stateRef: String) =
        Token().apply {
            this.stateRef = stateRef
            tag = ""
            ownerHash = ""
            amount = TokenAmount()
        }
}
