package net.corda.ledger.utxo.token.cache.impl.handlers

import net.corda.data.flow.event.FlowEvent
import net.corda.ledger.utxo.token.cache.entities.ClaimRelease
import net.corda.ledger.utxo.token.cache.entities.ForceClaimRelease
import net.corda.ledger.utxo.token.cache.entities.PoolCacheState
import net.corda.ledger.utxo.token.cache.entities.TokenCache
import net.corda.ledger.utxo.token.cache.entities.TokenPoolCache
import net.corda.ledger.utxo.token.cache.factories.RecordFactory
import net.corda.ledger.utxo.token.cache.handlers.TokenClaimReleaseEventHandler
import net.corda.ledger.utxo.token.cache.handlers.TokenForceClaimReleaseEventHandler
import net.corda.ledger.utxo.token.cache.impl.POOL_KEY
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
    private val tokenPoolCache: TokenPoolCache = mock() {
        whenever(it.get(any())).thenReturn(tokenCache)
    }
    private val poolCacheState: PoolCacheState = mock()
    private val recordFactory: RecordFactory = mock()

    private val tokenRef1 = "r1"
    private val claimId = "r1"
    private val externalEventRequestId = "ext1"
    private val flowId = "f1"

    @Test
    fun `release returns an ack response event`() {
        val response = Record<String, FlowEvent>("", "", null)
        whenever(recordFactory.getClaimReleaseAck(flowId, externalEventRequestId)).thenReturn(response)

        val target = TokenClaimReleaseEventHandler(recordFactory)
        val claimRelease = createClaimRelease()
        val result = target.handle(tokenPoolCache, poolCacheState, claimRelease)
        Assertions.assertThat(result).isSameAs(response)
    }

    @Test
    fun `when claim not found do nothing`() {
        val target = TokenClaimReleaseEventHandler(recordFactory)
        val claimRelease = createClaimRelease()
        whenever(poolCacheState.claimExists(claimId)).thenReturn(false)

        val result = target.handle(tokenPoolCache, poolCacheState, claimRelease)

        Assertions.assertThat(result).isNull()
        verify(tokenCache, never()).removeAll(any())
    }

    @Test
    fun `when claim found remove it from the state`() {
        val target = TokenClaimReleaseEventHandler(recordFactory)
        val claimRelease = createClaimRelease()
        whenever(poolCacheState.claimExists(claimId)).thenReturn(true)

        val result = target.handle(tokenPoolCache, poolCacheState, claimRelease)

        Assertions.assertThat(result).isNull()
        verify(poolCacheState).removeClaim(claimId)
    }

    @Test
    fun `when claim found remove any used states`() {
        val target = TokenClaimReleaseEventHandler(recordFactory)
        val claimRelease = createClaimRelease()
        whenever(poolCacheState.claimExists(claimId)).thenReturn(true)

        val result = target.handle(tokenPoolCache, poolCacheState, claimRelease)

        Assertions.assertThat(result).isNull()
        verify(tokenCache).removeAll(setOf(tokenRef1))
    }

    private fun createClaimRelease(): ClaimRelease {
        return ClaimRelease(claimId, externalEventRequestId, flowId, setOf(tokenRef1), POOL_KEY)
    }
}

class TokenForceClaimReleaseEventHandlerTest {

    private val tokenCache: TokenCache = mock()
    private val tokenPoolCache: TokenPoolCache = mock() {
        whenever(it.get(any())).thenReturn(tokenCache)
    }
    private val poolCacheState: PoolCacheState = mock()
    private val claimId = "r1"

    @Test
    fun `when claim not found do nothing`() {
        val target = TokenForceClaimReleaseEventHandler()
        val claimRelease = createForceClaimRelease()
        whenever(poolCacheState.claimExists(claimId)).thenReturn(false)

        val result = target.handle(tokenPoolCache, poolCacheState, claimRelease)

        Assertions.assertThat(result).isNull()
        verify(poolCacheState, never()).removeClaim(any())
    }

    @Test
    fun `when claim found remove it from the state`() {
        val target = TokenForceClaimReleaseEventHandler()
        val claimRelease = createForceClaimRelease()
        whenever(poolCacheState.claimExists(claimId)).thenReturn(true)

        val result = target.handle(tokenPoolCache, poolCacheState, claimRelease)

        Assertions.assertThat(result).isNull()
        verify(poolCacheState).removeClaim(claimId)
    }

    private fun createForceClaimRelease(): ForceClaimRelease {
        return ForceClaimRelease(claimId, POOL_KEY)
    }
}
