package net.corda.services.token

import net.corda.data.services.TokenSetKey
import net.corda.data.services.TokenState
import net.corda.messaging.api.processor.StateAndEventProcessor

interface TokenEventHandler<T:Any> {
    fun handle(
        key: TokenSetKey,
        state: TokenState,
        event: T
    ): StateAndEventProcessor.Response<TokenState>
}