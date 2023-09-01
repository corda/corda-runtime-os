package net.corda.ledger.utxo.token.cache.services

import net.corda.data.ledger.utxo.token.selection.data.TokenAmount
import net.corda.data.ledger.utxo.token.selection.event.TokenPoolCacheEvent
import net.corda.data.ledger.utxo.token.selection.key.TokenPoolCacheKey
import net.corda.data.ledger.utxo.token.selection.state.TokenPoolCacheState
import net.corda.ledger.utxo.token.cache.converters.EntityConverter
import net.corda.ledger.utxo.token.cache.converters.EventConverter
import net.corda.ledger.utxo.token.cache.entities.PoolCacheState
import net.corda.ledger.utxo.token.cache.entities.TokenCache
import net.corda.ledger.utxo.token.cache.entities.TokenEvent
import net.corda.ledger.utxo.token.cache.handlers.TokenEventHandler
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.math.BigInteger

class TokenCacheEventProcessor constructor(
    private val eventConverter: EventConverter,
    private val entityConverter: EntityConverter,
    private val tokenCacheEventHandlerMap: Map<Class<*>, TokenEventHandler<in TokenEvent>>,
) : StateAndEventProcessor<TokenPoolCacheKey, TokenPoolCacheState, TokenPoolCacheEvent> {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override val keyClass = TokenPoolCacheKey::class.java

    override val eventValueClass = TokenPoolCacheEvent::class.java

    override val stateValueClass = TokenPoolCacheState::class.java

    override fun onNext(
        state: TokenPoolCacheState?,
        event: Record<TokenPoolCacheKey, TokenPoolCacheEvent>
    ): StateAndEventProcessor.Response<TokenPoolCacheState> {

        try {
            val tokenEvent = eventConverter.convert(event.value)

            val nonNullableState = state ?: TokenPoolCacheState().apply {
                this.poolKey = event.key
                this.availableTokens = listOf()
                this.tokenClaims = listOf()
            }

            val tokenCache = entityConverter.toTokenCache(nonNullableState)
            val poolCacheState = entityConverter.toPoolCacheState(nonNullableState)

            val handler = checkNotNull(tokenCacheEventHandlerMap[tokenEvent.javaClass]) {
                "Received an event with and unrecognized payload '${tokenEvent.javaClass}'"
            }

            val sb = StringBuffer()
            sb.appendLine(
                "Token Selection Request type = ${handler.javaClass.simpleName} Pool = ${nonNullableState.poolKey}"
            )
            sb.appendLine("Before Handler:")
            sb.append(getTokenSummary(tokenCache, poolCacheState))

            val result = handler.handle(tokenCache, poolCacheState, tokenEvent)
                ?: return StateAndEventProcessor.Response(poolCacheState.toAvro(), listOf())

            sb.appendLine("After Handler:")
            sb.append(getTokenSummary(tokenCache, poolCacheState))
            log.info(sb.toString())

            return StateAndEventProcessor.Response(
                poolCacheState.toAvro(),
                listOf(result)
            )
        } catch (e: Exception) {
            log.error("Unexpected error while processing event '${event}'. The event will be sent to the DLQ.", e)
            return StateAndEventProcessor.Response(state, listOf(), markForDLQ = true)
        }
    }

    private fun getTokenSummary(tokenCache: TokenCache, state: PoolCacheState): String {
        val cachedTokenValue = tokenCache.sumOf { it.amount }
        val cachedTokenCount = tokenCache.count()
        val avroState = state.toAvro()
        val sb = StringBuffer()
        sb.appendLine("Token Cache Summary:")
        sb.appendLine("Token Balance: $cachedTokenValue")
        sb.appendLine("Token Count  : $cachedTokenCount")
        val tokenIndex = tokenCache.associateBy { it.stateRef }

        avroState.tokenClaims.forEach {
            val claimedTokens = it.claimedTokenStateRefs.mapNotNull { ref-> tokenIndex[ref] }
            val claimCount = claimedTokens.size
            val claimBalance = claimedTokens.sumOf { token-> token.amount }
            val tokens = claimedTokens.joinToString(" ") { token-> "(${token.amount})" }
            sb.appendLine(
                "Claim       : ${it.claimId} Token Count $claimCount Token Balance $claimBalance Tokens:${tokens}"
            )
        }
        return sb.toString()
    }

    private fun amountToBigDecimal(avroTokenAmount: TokenAmount): BigDecimal {
        val unscaledValueBytes = ByteArray(avroTokenAmount.unscaledValue.remaining())
            .apply { avroTokenAmount.unscaledValue.get(this) }

        avroTokenAmount.unscaledValue.position(0)
        return BigDecimal(
            BigInteger(unscaledValueBytes),
            avroTokenAmount.scale
        )
    }
}
