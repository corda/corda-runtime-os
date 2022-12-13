package net.corda.ledger.utxo.impl.token.selection.factories

import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.ledger.utxo.token.selection.data.Token
import net.corda.data.ledger.utxo.token.selection.data.TokenAmount
import net.corda.data.ledger.utxo.token.selection.data.TokenLedgerChange
import net.corda.data.ledger.utxo.token.selection.event.TokenPoolCacheEvent
import net.corda.data.ledger.utxo.token.selection.key.TokenPoolCacheKey
import net.corda.flow.external.events.factory.ExternalEventFactory
import net.corda.flow.external.events.factory.ExternalEventRecord
import net.corda.flow.state.FlowCheckpoint
import net.corda.schema.Schemas
import org.osgi.service.component.annotations.Component
import java.math.BigDecimal
import java.nio.ByteBuffer

// HACK: This class has been added for testing will be removed by CORE-5722 (ledger integration)
@Component(service = [ExternalEventFactory::class])
class TokenUpdateExternalEventFactory : ExternalEventFactory<TokenUpdateParameters, String, String> {

    override val responseType = String::class.java

    override fun createExternalEvent(
        checkpoint: FlowCheckpoint,
        flowExternalEventContext: ExternalEventContext,
        parameters: TokenUpdateParameters
    ): ExternalEventRecord {
        val keyToken = (parameters.newTokens + parameters.consumedTokens).first()

        val key = TokenPoolCacheKey().apply {
            this.shortHolderId = checkpoint.holdingIdentity.shortHash.value
            this.tokenType = keyToken.tokenType
            this.issuerHash = keyToken.issuerHash.toString()
            this.notaryX500Name = keyToken.notaryX500Name.toString()
            this.symbol = keyToken.symbol
        }

        val ledgerChangeEvent = TokenLedgerChange().apply {
            poolKey = key
            this.producedTokens = parameters.newTokens.map {
                Token().apply {
                    this.stateRef = it.stateRef.toString()
                    this.tag = it.tag
                    this.ownerHash = it.ownerHash?.toString()
                    this.amount = toTokenAmount(it.amount)
                }
            }
            this.consumedTokens = parameters.consumedTokens.map {
                Token().apply {
                    this.stateRef = it.stateRef.toString()
                    this.tag = it.tag
                    this.ownerHash = it.ownerHash?.toString()
                    this.amount = toTokenAmount(it.amount)
                }
            }
        }

        return ExternalEventRecord(Schemas.Services.TOKEN_CACHE_EVENT, key, TokenPoolCacheEvent(key, ledgerChangeEvent))
    }

    override fun resumeWith(checkpoint: FlowCheckpoint, response: String): String {
        return response
    }

    private fun toTokenAmount(amount: BigDecimal): TokenAmount {
        return TokenAmount().apply {
            unscaledValue = ByteBuffer.wrap(amount.unscaledValue().toByteArray())
            scale = amount.scale()
        }
    }
}