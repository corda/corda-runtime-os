package net.corda.services.token

import net.corda.data.flow.event.FlowEvent
import net.corda.data.services.TokenClaimResult
import net.corda.data.services.TokenEvent
import net.corda.data.services.TokenSetKey
import net.corda.messaging.api.records.Record
import java.time.Instant

interface TokenRecordFactory {

    fun createTimeout(tokenSetKey: TokenSetKey): Record<TokenSetKey, TokenEvent>

    fun getQueryClaimResponse(tokenClaimResult: TokenClaimResult, timestamp: Instant): Record<String, FlowEvent>
}