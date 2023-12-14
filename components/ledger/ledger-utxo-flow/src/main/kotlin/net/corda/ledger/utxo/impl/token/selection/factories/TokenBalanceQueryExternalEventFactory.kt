package net.corda.ledger.utxo.impl.token.selection.factories

import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.ledger.utxo.token.selection.data.TokenAmount
import net.corda.data.ledger.utxo.token.selection.data.TokenBalanceQuery
import net.corda.data.ledger.utxo.token.selection.data.TokenBalanceQueryResult
import net.corda.data.ledger.utxo.token.selection.event.TokenPoolCacheEvent
import net.corda.data.ledger.utxo.token.selection.key.TokenPoolCacheKey
import net.corda.flow.external.events.factory.ExternalEventFactory
import net.corda.flow.external.events.factory.ExternalEventRecord
import net.corda.flow.state.FlowCheckpoint
import net.corda.ledger.utxo.impl.token.selection.impl.TokenBalanceImpl
import net.corda.schema.Schemas
import net.corda.v5.ledger.utxo.token.selection.TokenBalance
import net.corda.v5.ledger.utxo.token.selection.TokenBalanceCriteria
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import java.math.BigDecimal
import java.math.BigInteger

@Component(service = [ExternalEventFactory::class])
class TokenBalanceQueryExternalEventFactory @Activate constructor() :
    ExternalEventFactory<TokenBalanceCriteria, TokenBalanceQueryResult, TokenBalance> {

    override val responseType = TokenBalanceQueryResult::class.java

    override fun createExternalEvent(
        checkpoint: FlowCheckpoint,
        flowExternalEventContext: ExternalEventContext,
        parameters: TokenBalanceCriteria
    ): ExternalEventRecord {
        val key = TokenPoolCacheKey().apply {
            this.shortHolderId = checkpoint.holdingIdentity.shortHash.value
            this.tokenType = parameters.tokenType
            this.issuerHash = parameters.issuerHash.toString()
            this.symbol = parameters.symbol
            this.notaryX500Name = parameters.notaryX500Name.toString()
        }

        val balanceQuery = TokenBalanceQuery().apply {
            this.poolKey = key
            this.requestContext = flowExternalEventContext
            this.ownerHash = parameters.ownerHash?.toString()
            this.tagRegex = parameters.tagRegex
        }

        return ExternalEventRecord(Schemas.Services.TOKEN_CACHE_EVENT, key, TokenPoolCacheEvent(key, balanceQuery))
    }

    override fun resumeWith(checkpoint: FlowCheckpoint, response: TokenBalanceQueryResult): TokenBalance {
        return TokenBalanceImpl(response.availableBalance.toBigDecimal(), response.totalBalance.toBigDecimal())
    }

    private fun TokenAmount.toBigDecimal() =
        BigDecimal(
            BigInteger(
                ByteArray(unscaledValue.remaining())
                    .apply { unscaledValue.get(this) }
            ),
            scale
        )
}
