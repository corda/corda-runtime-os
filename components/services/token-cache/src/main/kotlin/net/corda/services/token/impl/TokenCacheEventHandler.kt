package net.corda.services.token.impl

import net.corda.data.flow.event.FlowEvent
import net.corda.data.services.Token
import net.corda.data.services.TokenClaimQuery
import net.corda.data.services.TokenClaimResult
import net.corda.data.services.TokenEvent
import net.corda.data.services.TokenLedgerEvent
import net.corda.data.services.TokenSetKey
import net.corda.data.services.TokenState
import net.corda.data.services.TokenTimeoutCheckEvent
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import net.corda.services.token.TokenRecordFactory
import net.corda.v5.base.util.contextLogger

class TokenCacheEventHandler(
    private val tokenRecordFactory: TokenRecordFactory,
    private val config: SmartConfig
) : StateAndEventProcessor<TokenSetKey, TokenState, TokenEvent> {

    private companion object {
        val log = contextLogger()
    }

    override val keyClass = TokenSetKey::class.java

    override val eventValueClass = TokenEvent::class.java

    override val stateValueClass = TokenState::class.java

    override fun onNext(
        state: TokenState?,
        event: Record<TokenSetKey, TokenEvent>
    ): StateAndEventProcessor.Response<TokenState> {
        val key = event.key

        if (event.value == null || event.value?.payload == null) {
            log.error("Received and event with a null payload. for '${event.key}'")
            return StateAndEventProcessor.Response(state, listOf())
        }

        val actualState = state ?: TokenState().apply {
            tokenSetKey = key
            availableTokens = listOf()
            blockedClaimQueries = listOf()
            claimedTokens = listOf()
        }

        val eventValue = event.value!!

        return when (val payload = eventValue.payload) {
            is TokenLedgerEvent -> {
                onTokenLedgerEvent(key, payload, actualState)
            }
            is TokenClaimQuery -> {
                onTokenClaimQueryEvent(key, payload, actualState)
            }

            is TokenTimeoutCheckEvent -> {
                onTimeoutCheckEvent(event.key, actualState)
            }
            else -> {
                log.error("Received and event with unknown payload of '${payload.javaClass}'")
                return StateAndEventProcessor.Response(actualState, listOf())
            }
        }
    }

    private fun onTokenLedgerEvent(
        key: TokenSetKey,
        payload: TokenLedgerEvent,
        state: TokenState
    ): StateAndEventProcessor.Response<TokenState> {
        val toRemove = payload.consumedTokens.map { it.stateRef }.toSet()
        state.availableTokens = state.availableTokens
            .filterNot { toRemove.contains(it.stateRef) } + payload.producedTokens

        return StateAndEventProcessor.Response(state, listOf())
    }

    private fun onTokenClaimQueryEvent(
        key: TokenSetKey,
        payload: TokenClaimQuery,
        state: TokenState
    ): StateAndEventProcessor.Response<TokenState> {
        // For the PoC we implement a very basic select all tokens until target reached
        // this is fine for v1, but should be replaced with a more sophisticated implementation.
        val lockedTokens = state.claimedTokens.flatMap { it.claimedTokens }.toSet()

        val selectedTokens = mutableListOf<Token>()
        var remaining = payload.targetAmount
        for (availableToken in state.availableTokens.filterNot { lockedTokens.contains(it.stateRef) }) {
            if (remaining <= 0) {
                break
            }

            selectedTokens.add(availableToken)
            remaining -= availableToken.amount
        }

        // Found enough can respond
        if(remaining<=0){
            val tokenClaimResult = TokenClaimResult().apply{

            }

            StateAndEventProcessor.Response(state, tokenRecordFactory.getQueryClaimResponse())
        }

    }

    private fun onTimeoutCheckEvent(key: TokenSetKey, state: TokenState): StateAndEventProcessor.Response<TokenState> {
        TODO()
    }
}