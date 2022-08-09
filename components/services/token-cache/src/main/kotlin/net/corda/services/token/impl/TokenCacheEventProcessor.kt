package net.corda.services.token.impl

import net.corda.data.services.TokenEvent
import net.corda.data.services.TokenSetKey
import net.corda.data.services.TokenState
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import net.corda.services.token.TokenEventHandler
import net.corda.v5.base.util.contextLogger

class TokenCacheEventProcessor constructor(
    private val tokenCacheEventHandlerMap: Map<Class<*>, TokenEventHandler<in Any>>,
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
            tokenClaims = listOf()
        }

        val payload = event.value!!.payload
        val handler = tokenCacheEventHandlerMap[payload.javaClass]
        return if (handler == null) {
            log.error("Received an event with and unrecognised payload '${payload.javaClass}'")
            StateAndEventProcessor.Response(state, listOf())
        } else {
            handler.handle(key, actualState, payload)
        }
    }
}