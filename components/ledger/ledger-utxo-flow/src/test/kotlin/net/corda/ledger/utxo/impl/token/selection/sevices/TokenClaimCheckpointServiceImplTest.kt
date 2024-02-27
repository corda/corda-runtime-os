package net.corda.ledger.utxo.impl.token.selection.sevices

import net.corda.data.ledger.utxo.token.selection.key.TokenPoolCacheKey
import net.corda.flow.state.FlowCheckpoint
import net.corda.ledger.utxo.impl.token.selection.entities.TokenClaimCheckpointState
import net.corda.ledger.utxo.impl.token.selection.entities.TokenClaimsCheckpointState
import net.corda.ledger.utxo.impl.token.selection.factories.TokenClaimFactory
import net.corda.ledger.utxo.impl.token.selection.impl.PoolKey
import net.corda.ledger.utxo.impl.token.selection.services.TokenClaimCheckpointServiceImpl
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class TokenClaimCheckpointServiceImplTest {

    private val flowCheckpoint = mock<FlowCheckpoint>()
    private val tokenClaimFactory = mock<TokenClaimFactory>()
    private val target = TokenClaimCheckpointServiceImpl(tokenClaimFactory)

    @Test
    fun `add a claim to the flow checkpoint state creates default state`() {
        val claimID = "id1"
        val poolKey = TokenPoolCacheKey.newBuilder()
            .setShortHolderId("sh")
            .setTokenType("tt")
            .setNotaryX500Name("nx500")
            .setIssuerHash("ih")
            .setSymbol("sym")
            .build()

        // no current state
        whenever(flowCheckpoint.readCustomState(TokenClaimsCheckpointState::class.java)).thenReturn(null)

        val claimState = TokenClaimCheckpointState(
            "a",
            PoolKey("a", "b", "c", "d", "e")
        )
        whenever(tokenClaimFactory.createTokenClaimCheckpointState(claimID, poolKey)).thenReturn(claimState)

        target.addClaimToCheckpoint(flowCheckpoint, claimID, poolKey)

        val expectedState = TokenClaimsCheckpointState(mutableListOf(claimState))

        verify(flowCheckpoint).writeCustomState(expectedState)
    }

    @Test
    fun `add a claim to the flow checkpoint adds to existing state`() {
        val claimID = "id1"
        val poolKey = TokenPoolCacheKey.newBuilder()
            .setShortHolderId("sh")
            .setTokenType("tt")
            .setNotaryX500Name("nx500")
            .setIssuerHash("ih")
            .setSymbol("sym")
            .build()

        // no current state
        whenever(flowCheckpoint.readCustomState(TokenClaimsCheckpointState::class.java)).thenReturn(null)

        val claimState = TokenClaimCheckpointState(
            "a",
            PoolKey("a", "b", "c", "d", "e")
        )
        whenever(tokenClaimFactory.createTokenClaimCheckpointState(claimID, poolKey)).thenReturn(claimState)

        target.addClaimToCheckpoint(flowCheckpoint, claimID, poolKey)

        val expectedState = TokenClaimsCheckpointState(mutableListOf(claimState))

        verify(flowCheckpoint).writeCustomState(expectedState)
    }
}
