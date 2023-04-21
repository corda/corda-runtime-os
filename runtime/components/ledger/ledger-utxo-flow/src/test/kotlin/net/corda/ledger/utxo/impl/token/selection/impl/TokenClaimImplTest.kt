package net.corda.ledger.utxo.impl.token.selection.impl

import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.ledger.utxo.impl.token.selection.factories.ClaimReleaseExternalEventFactory
import net.corda.v5.ledger.utxo.StateRef
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify


class TokenClaimImplTest {

    private val externalEventExecutor = mock<ExternalEventExecutor>()

    @Test
    fun `useAndRelease sends used tokens`() {
        val key = PoolKey("","","","","")
        val stateRefs = listOf<StateRef>()

        TokenClaimImpl("c1", key, listOf(), externalEventExecutor).useAndRelease(stateRefs)

        verify(externalEventExecutor).execute(
            eq(ClaimReleaseExternalEventFactory::class.java),
            argThat { this.usedTokens == stateRefs && this.poolKey == key && this.claimId=="c1" }
        )
    }
}

