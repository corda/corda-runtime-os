package net.corda.ledger.utxo.token.cache.impl.handlers

import net.corda.data.flow.event.FlowEvent
import net.corda.ledger.utxo.token.cache.entities.ClaimRelease
import net.corda.ledger.utxo.token.cache.entities.PoolCacheState
import net.corda.ledger.utxo.token.cache.handlers.TokenClaimReleaseEventHandler
import net.corda.ledger.utxo.token.cache.impl.POOL_CACHE_KEY
import net.corda.ledger.utxo.token.cache.entities.TokenCache
import net.corda.ledger.utxo.token.cache.factories.RecordFactory
import net.corda.messaging.api.records.Record
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class TokenClaimReleaseEventHandlerTest {

    private val tokenCache: TokenCache = mock()
    private val poolCacheState: PoolCacheState = mock()
    private val recordFactory: RecordFactory = mock()

    private val tokenRef1 = "r1"
    private val claimId = "r1"
    private val flowId = "f1"

    @Test
    fun `release returns an ack response event`() {
        val response = Record<String, FlowEvent>("", "", null)
        whenever(recordFactory.getClaimReleaseAck(flowId, claimId)).thenReturn(response)

        val target = TokenClaimReleaseEventHandler(recordFactory)
        val claimRelease = createClaimRelease()
        val result = target.handle(tokenCache, poolCacheState, claimRelease)
        Assertions.assertThat(result).isSameAs(response)
    }

    @Test
    fun `when claim not found do nothing`() {
        val target = TokenClaimReleaseEventHandler(recordFactory)
        val claimRelease = createClaimRelease()
        whenever(poolCacheState.claimExists(claimId)).thenReturn(false)

        val result = target.handle(tokenCache, poolCacheState, claimRelease)

        Assertions.assertThat(result).isNull()
        verify(tokenCache, never()).removeAll(any())
    }

    @Test
    fun `when claim found remove it from the state`() {
        val target = TokenClaimReleaseEventHandler(recordFactory)
        val claimRelease = createClaimRelease()
        whenever(poolCacheState.claimExists(claimId)).thenReturn(true)

        val result = target.handle(tokenCache, poolCacheState, claimRelease)

        Assertions.assertThat(result).isNull()
        verify(poolCacheState).removeClaim(claimId)
    }

    @Test
    fun `when claim found remove any used states`() {
        val target = TokenClaimReleaseEventHandler(recordFactory)
        val claimRelease = createClaimRelease()
        whenever(poolCacheState.claimExists(claimId)).thenReturn(true)

        val result = target.handle(tokenCache, poolCacheState, claimRelease)

        Assertions.assertThat(result).isNull()
        verify(tokenCache).removeAll(setOf(tokenRef1))
    }

    private fun createClaimRelease(): ClaimRelease {
        return ClaimRelease(claimId, flowId, setOf(tokenRef1), POOL_CACHE_KEY)
    }
}
