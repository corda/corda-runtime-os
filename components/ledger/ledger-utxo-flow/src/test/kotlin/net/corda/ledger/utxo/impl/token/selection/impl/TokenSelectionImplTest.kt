package net.corda.ledger.utxo.impl.token.selection.impl

import net.corda.crypto.core.SecureHashImpl
import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.flow.token.query.TokenClaimCriteriaParameters
import net.corda.ledger.utxo.impl.token.selection.factories.TokenClaimQueryExternalEventFactory
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.utxo.token.selection.TokenClaim
import net.corda.v5.ledger.utxo.token.selection.TokenClaimCriteria
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal

class TokenSelectionImplTest {

    private val externalEventExecutor = mock<ExternalEventExecutor>()

    @Test
    fun `tryClaim executes external event with criteria`() {
        val uniqueID = "dedupeID"
        val criteria = TokenClaimCriteria(
            "tt",
            SecureHashImpl("SHA-256", byteArrayOf(1)),
            MemberX500Name.parse("CN=user1, O=user1 Corp, L=LDN, C=GB"),
            "s",
            BigDecimal(1)
        )

        val tokenClaim = mock<TokenClaim>()

        whenever(
            externalEventExecutor.execute(
                TokenClaimQueryExternalEventFactory::class.java,
                TokenClaimCriteriaParameters(uniqueID, criteria)
            )
        ).thenReturn(tokenClaim)

        Assertions.assertThat(TokenSelectionImpl(externalEventExecutor).tryClaim(uniqueID, criteria))
            .isEqualTo(tokenClaim)
    }

    @Test
    fun `try claim with a large id fails`() {
        assertThrows<IllegalArgumentException> {
            TokenSelectionImpl(externalEventExecutor).tryClaim("uniqueID".repeat(100), mock())
        }
    }
}
