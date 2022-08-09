package net.corda.services.token

import net.corda.data.services.TokenEvent
import net.corda.data.services.TokenSetKey
import net.corda.data.services.TokenState
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.processor.StateAndEventProcessor

interface TokenCacheEventProcessorFactory {

    fun create(config: SmartConfig): StateAndEventProcessor<TokenSetKey, TokenState, TokenEvent>
}