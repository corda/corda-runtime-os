package net.corda.services.token

import net.corda.data.services.TokenSetKey
import net.corda.data.services.TokenState
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.subscription.listener.StateAndEventListener

interface ClaimTimeoutScheduler : StateAndEventListener<TokenSetKey, TokenState>{
    /**
     * Called when the worker configuration changes, the scheduler uses the messaging configuration section
     * when publishing the scheduled wakeup events.
     *
     * @param config map of the worker's configuration sections
     */
    fun onConfigChange(config: Map<String, SmartConfig>)
}