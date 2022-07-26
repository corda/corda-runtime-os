package net.corda.services.token

import net.corda.data.flow.event.FlowEvent
import net.corda.data.services.TokenClaimResult
import net.corda.data.services.TokenSetKey
import net.corda.data.services.TokenTimeoutCheckEvent
import net.corda.messaging.api.records.Record

interface TokenRecordFactory {

    fun createTimeout(tokenSetKey: TokenSetKey): Record<TokenSetKey, TokenTimeoutCheckEvent>

    fun getQueryClaimResponse(TokenClaimResult: TokenClaimResult): Record<String, FlowEvent>
}