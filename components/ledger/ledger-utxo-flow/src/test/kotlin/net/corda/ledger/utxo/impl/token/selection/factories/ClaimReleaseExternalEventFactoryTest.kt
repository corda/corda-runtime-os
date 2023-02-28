package net.corda.ledger.utxo.impl.token.selection.factories

import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.ledger.utxo.token.selection.data.TokenClaimRelease
import net.corda.data.ledger.utxo.token.selection.event.TokenPoolCacheEvent
import net.corda.flow.external.events.factory.ExternalEventRecord
import net.corda.flow.state.FlowCheckpoint
import net.corda.ledger.utxo.impl.token.selection.impl.PoolKey
import net.corda.ledger.utxo.impl.token.selection.impl.toStateRef
import net.corda.schema.Schemas.Services.TOKEN_CACHE_EVENT
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class ClaimReleaseExternalEventFactoryTest {

    @Test
    fun `createExternalEvent should return a token release event`() {
        val stateRef = "s1".toStateRef()
        val poolKey = PoolKey("", "", "", "", "")
        val avroPoolKey = poolKey.toTokenPoolCacheKey()
        val checkpoint = mock<FlowCheckpoint>()
        val flowExternalEventContext = ExternalEventContext()
        val parameters = ClaimReleaseParameters("c1", poolKey, listOf(stateRef))

        val result = ClaimReleaseExternalEventFactory().createExternalEvent(
            checkpoint,
            flowExternalEventContext,
            parameters
        )

        val expectedReleaseEvent = TokenClaimRelease().apply {
            this.claimId = "c1"
            this.poolKey = avroPoolKey
            this.requestContext = flowExternalEventContext
            this.usedTokenStateRefs = listOf(stateRef.toString())
        }

        val expectedRecord = ExternalEventRecord(
            TOKEN_CACHE_EVENT,
            avroPoolKey,
            TokenPoolCacheEvent(avroPoolKey, expectedReleaseEvent)
        )

        assertThat(result.topic).isEqualTo(TOKEN_CACHE_EVENT)
        assertThat(result.key).isEqualTo(avroPoolKey)
        assertThat(result).isEqualTo(expectedRecord)
    }
}
