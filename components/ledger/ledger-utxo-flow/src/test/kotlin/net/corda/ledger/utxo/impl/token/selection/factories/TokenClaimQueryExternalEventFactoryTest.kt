package net.corda.ledger.utxo.impl.token.selection.impl.factories

import net.corda.data.KeyValuePairList
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.ledger.utxo.token.selection.data.Token
import net.corda.data.ledger.utxo.token.selection.data.TokenAmount
import net.corda.data.ledger.utxo.token.selection.data.TokenClaimQuery
import net.corda.data.ledger.utxo.token.selection.data.TokenClaimQueryResult
import net.corda.data.ledger.utxo.token.selection.data.TokenClaimResultStatus
import net.corda.data.ledger.utxo.token.selection.event.TokenPoolCacheEvent
import net.corda.data.ledger.utxo.token.selection.key.TokenPoolCacheKey
import net.corda.flow.external.events.factory.ExternalEventRecord
import net.corda.flow.state.FlowCheckpoint
import net.corda.ledger.utxo.impl.token.selection.factories.TokenClaimFactory
import net.corda.ledger.utxo.impl.token.selection.factories.TokenClaimQueryExternalEventFactory
import net.corda.ledger.utxo.impl.token.selection.impl.ALICE_X500_HOLDING_ID
import net.corda.ledger.utxo.impl.token.selection.impl.BOB_X500_NAME
import net.corda.ledger.utxo.impl.token.selection.impl.toSecureHash
import net.corda.schema.Schemas.Services.TOKEN_CACHE_EVENT
import net.corda.v5.ledger.utxo.token.selection.ClaimedToken
import net.corda.v5.ledger.utxo.token.selection.TokenClaim
import net.corda.v5.ledger.utxo.token.selection.TokenClaimCriteria
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.nio.ByteBuffer

class TokenClaimQueryExternalEventFactoryTest {
    private val tokenType = "tt"
    private val symbol = "s"
    private val issuerHash = "issuer".toSecureHash()
    private val notaryX500Name = BOB_X500_NAME
    private val key = TokenPoolCacheKey.newBuilder()
        .setShortHolderId(ALICE_X500_HOLDING_ID.shortHash.value)
        .setTokenType(tokenType)
        .setIssuerHash(issuerHash.toString())
        .setNotaryX500Name(notaryX500Name.toString())
        .setSymbol(symbol)
        .build()

    private val checkpoint = mock<FlowCheckpoint>().apply {
        whenever(holdingIdentity).thenReturn(ALICE_X500_HOLDING_ID)
    }

    @Test
    fun `createExternalEvent should return claim query event record`() {
        val ownerHash = "owner".toSecureHash()
        val tagRegex = "tag"
        val amount = BigDecimal(10)
        val tokenAmount = TokenAmount(
            amount.scale(),
            ByteBuffer.wrap(amount.unscaledValue().toByteArray())
        )
        val flowExternalEventContext = ExternalEventContext("r1", "f1", KeyValuePairList())

        val parameters = TokenClaimCriteria(tokenType, issuerHash, notaryX500Name, symbol, amount).apply {
            this.tagRegex = tagRegex
            this.ownerHash = ownerHash
        }

        val expectedClaimQuery = TokenClaimQuery().apply {
            this.poolKey = key
            this.requestContext = flowExternalEventContext
            this.ownerHash = ownerHash.toString()
            this.tagRegex = tagRegex
            this.targetAmount = tokenAmount
        }

        val expectedExternalEventRecord = ExternalEventRecord(
            TOKEN_CACHE_EVENT,
            key,
            TokenPoolCacheEvent(key, expectedClaimQuery)
        )

        val target = TokenClaimQueryExternalEventFactory(mock())

        val result = target.createExternalEvent(checkpoint, flowExternalEventContext, parameters)

        assertThat(result).isEqualTo(expectedExternalEventRecord)
    }

    @Test
    fun `resumeWith returns token claim when successful`() {
        val token = Token()
        val expectedToken = mock<ClaimedToken>()
        val tokenClaim = mock<TokenClaim>()

        val tokenClaimFactory = mock<TokenClaimFactory>().apply {
            whenever(createClaimedToken(key, token)).thenReturn(expectedToken)
            whenever(createTokenClaim("c1", key, listOf(expectedToken))).thenReturn(tokenClaim)
        }

        val response = TokenClaimQueryResult().apply {
            this.poolKey = key
            this.claimId = "c1"
            this.claimedTokens = listOf(token)
            this.resultType = TokenClaimResultStatus.SUCCESS
        }

        val target = TokenClaimQueryExternalEventFactory(tokenClaimFactory)

        val result = target.resumeWith(checkpoint, response)

        assertThat(result).isSameAs(tokenClaim)
    }

    @Test
    fun `resumeWith returns null when none found`() {
        val response = TokenClaimQueryResult().apply {
            this.poolKey = key
            this.claimId = "c1"
            this.claimedTokens = listOf()
            this.resultType = TokenClaimResultStatus.NONE_AVAILABLE
        }

        val target = TokenClaimQueryExternalEventFactory(mock())

        val result = target.resumeWith(checkpoint, response)

        assertThat(result).isNull()
    }
}
