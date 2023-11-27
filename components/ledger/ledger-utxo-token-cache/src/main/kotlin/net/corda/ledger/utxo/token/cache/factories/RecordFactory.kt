package net.corda.ledger.utxo.token.cache.factories

import net.corda.data.flow.event.FlowEvent
import net.corda.data.ledger.utxo.token.selection.data.Token
import net.corda.ledger.utxo.token.cache.entities.CachedToken
import net.corda.ledger.utxo.token.cache.entities.TokenPoolKey
import net.corda.messaging.api.records.Record
import net.corda.v5.ledger.utxo.token.selection.TokenBalance

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
        poolKey: TokenPoolKey,
        selectedTokens: List<CachedToken>
    ): Record<String, FlowEvent>

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
    fun getSuccessfulClaimResponseWithListTokens(
        flowId: String,
        externalEventRequestId: String,
        poolKey: TokenPoolKey,
        selectedTokens: List<Token>
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
        poolKey: TokenPoolKey
    ): Record<String, FlowEvent>

    /**
     * Creates a [Record] to acknowledge the release of a claim
     *
     * @param flowId The unique identifier of the flow that requested the claim release
     * @param externalEventRequestId The unique ID of the flow request event of the claim release

     * @return A [FlowEvent] response record for the release acknowledgement
     */
    fun getClaimReleaseAck(
        flowId: String,
        externalEventRequestId: String
    ): Record<String, FlowEvent>

    /**
     * Creates a balance response record
     *
     * @param flowId The unique identifier of the flow that requested the balance
     * @param externalEventRequestId The unique ID of the flow request event of the balance query
     * @param poolKey The unique key of the pool of tokens which the balance was calculated from
     * @param tokenBalance The balance of the pool of tokens
     *
     * @return A [FlowEvent] response record for a balance query
     */
    fun getBalanceResponse(
        flowId: String,
        externalEventRequestId: String,
        poolKey: TokenPoolKey,
        tokenBalance: TokenBalance
    ): Record<String, FlowEvent>
}

