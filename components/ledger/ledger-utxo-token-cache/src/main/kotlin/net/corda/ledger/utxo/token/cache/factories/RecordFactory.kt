package net.corda.ledger.utxo.token.cache.factories

import net.corda.data.flow.event.FlowEvent
import net.corda.data.ledger.utxo.token.selection.key.TokenPoolCacheKey
import net.corda.messaging.api.records.Record
import net.corda.ledger.utxo.token.cache.entities.CachedToken

/**
 * The [RecordFactory] creates instances of [Record]
 */
interface RecordFactory {

    /**
     * Creates a token claim response record for a successful claim
     *
     * @param flowId The unique identifier of the flow that requested the claim
     * @param externalEventRequestId The unique ID of the flow request event of the claim
     * @param poolKey The unique key of the pool of tokens the claim was for
     * @param selectedTokens The list of tokens that were selected by the claim
     *
     * @return A [FlowEvent] response record for the claim
     */
    fun getSuccessfulClaimResponse(
        flowId: String,
        externalEventRequestId: String,
        poolKey: TokenPoolCacheKey,
        selectedTokens: List<CachedToken>
    ): Record<String, FlowEvent>

    /**
     * Creates a [Record] for a failed claim attempt
     *
     * @param flowId The unique identifier of the flow that requested the claim
     * @param externalEventRequestId The unique ID of the flow request event of the claim
     * @param poolKey The unique key of the pool of tokens the claim was for

     * @return A [FlowEvent] response record for the claim
     */
    fun getFailedClaimResponse(
        flowId: String,
        externalEventRequestId: String,
        poolKey: TokenPoolCacheKey
    ): Record<String, FlowEvent>

    /**
     * Creates a [Record] to acknowledge the release of a claim
     *
     * @param flowId The unique identifier of the flow that requested the claim
     * @param claimId The unique ID of the claim that was released

     * @return A [FlowEvent] response record for the release acknowledgement
     */
    fun getClaimReleaseAck(
        flowId: String,
        claimId: String
    ): Record<String, FlowEvent>
}

