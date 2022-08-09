package net.corda.services.token.impl

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.external.ExternalEventResponse
import net.corda.data.services.TokenClaimResult
import net.corda.data.services.TokenEvent
import net.corda.data.services.TokenSetKey
import net.corda.data.services.TokenTimeoutCheck
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import net.corda.services.token.TokenRecordFactory
import java.time.Instant

class TokenRecordFactoryImpl : TokenRecordFactory {

    override fun createTimeout(tokenSetKey: TokenSetKey): Record<TokenSetKey, TokenEvent> {
        val event = TokenEvent().apply {
            this.payload = TokenTimeoutCheck()
            this.tokenSetKey = tokenSetKey
        }
        return Record(Schemas.Services.TOKEN_CACHE_EVENT, tokenSetKey, event)
    }

    override fun getQueryClaimResponse(tokenClaimResult: TokenClaimResult, timestamp: Instant): Record<String, FlowEvent> {
        val responseMessage = ExternalEventResponse().apply {
            this.requestId = tokenClaimResult.requestContext.requestId
            this.payload = tokenClaimResult
            this.timestamp = timestamp
        }

        val flowEvent = FlowEvent().apply {
            this.flowId = tokenClaimResult.requestContext.flowId
            this.payload = responseMessage
        }

        return Record(Schemas.Flow.FLOW_EVENT_TOPIC, tokenClaimResult.requestContext.flowId, flowEvent)
    }
}