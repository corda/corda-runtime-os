package net.corda.services.token.impl

import net.corda.data.services.TokenEvent
import net.corda.data.services.TokenSetKey
import net.corda.data.services.TokenState
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.services.token.TokenCacheEventProcessorFactory
import net.corda.services.token.TokenEventHandler

class TokenCacheEventProcessorFactoryImpl constructor(
    private val tokenCacheEventHandlerMap: Map<Class<*>, TokenEventHandler<in Any>>
) : TokenCacheEventProcessorFactory {

    override fun create(config: SmartConfig): StateAndEventProcessor<TokenSetKey, TokenState, TokenEvent> {
        return TokenCacheEventProcessor(tokenCacheEventHandlerMap)
    }
}