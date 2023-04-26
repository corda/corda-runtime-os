package net.corda.ledger.utxo.impl.token.selection.factories

import java.math.BigDecimal
import java.math.BigInteger
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.ledger.utxo.token.selection.data.TokenAmount
import net.corda.data.ledger.utxo.token.selection.data.TokenBalanceQuery
import net.corda.data.ledger.utxo.token.selection.data.TokenBalanceQueryResult
import net.corda.data.ledger.utxo.token.selection.event.TokenPoolCacheEvent
import net.corda.data.ledger.utxo.token.selection.key.TokenPoolCacheKey
import net.corda.flow.external.events.factory.ExternalEventFactory
import net.corda.flow.external.events.factory.ExternalEventRecord
import net.corda.flow.state.FlowCheckpoint
import net.corda.schema.Schemas
import net.corda.v5.ledger.utxo.observer.UtxoTokenPoolKey
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component

@Component(service = [ExternalEventFactory::class])
class TokenBalanceQueryExternalEventFactory @Activate constructor() :
    ExternalEventFactory<UtxoTokenPoolKey, TokenBalanceQueryResult, BigDecimal> {

    override val responseType = TokenBalanceQueryResult::class.java

    // Method that sends the message
    override fun createExternalEvent(
        checkpoint: FlowCheckpoint,
        flowExternalEventContext: ExternalEventContext,
        parameters: UtxoTokenPoolKey
    ): ExternalEventRecord {
        val key = TokenPoolCacheKey().apply {
            this.shortHolderId = checkpoint.holdingIdentity.shortHash.value
            this.tokenType = parameters.tokenType
            this.issuerHash = parameters.issuerHash.toString()
            this.symbol = parameters.symbol
        }

        val balanceQuery = TokenBalanceQuery().apply {
            this.poolKey = key
            this.requestContext = flowExternalEventContext
        }

        return ExternalEventRecord(Schemas.Services.TOKEN_CACHE_EVENT, key, TokenPoolCacheEvent(key, balanceQuery))
    }

    // Callback that is invoked when the message is received
    // Todo: Should the balance be a BigDecimal? Or is there a more appropriate type? Avro also needs to support the type
    override fun resumeWith(checkpoint: FlowCheckpoint, response: TokenBalanceQueryResult): BigDecimal {
        return response.balance.toBigDecimal()
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
