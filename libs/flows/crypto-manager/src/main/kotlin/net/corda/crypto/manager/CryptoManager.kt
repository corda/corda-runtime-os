package net.corda.crypto.manager

import net.corda.data.crypto.wire.ops.flow.FlowOpsRequest
import net.corda.data.crypto.wire.ops.flow.FlowOpsResponse
import net.corda.data.flow.state.crypto.CryptoState
import net.corda.libs.configuration.SmartConfig
import java.time.Instant

interface CryptoManager {

    /**
     * Generate a CryptoState object to represent a DB request that needs to be sent
     * @param requestId Unique ID for this request.
     * @param request Request to be sent
     * @return CryptoState object with request stored and ready to be sent
     */
    fun processMessageToSend(requestId: String, request: FlowOpsRequest) : CryptoState

    /**
     * Process a crypto response. Compare to the crypto state objects request. If the response matches the request, store the response in 
     * the crypto state.
     * @param cryptoState State representing the last sent request
     * @param response Response received
     * @return Updated CryptoState object. If the response matches the request the response is saved to the crypto state object.
     */
    fun processMessageReceived(cryptoState: CryptoState, response: FlowOpsResponse) : CryptoState

    /**
     * Checks the crypto state to see if there is a request to send.
     * Request will be returned if the crypto state request is not null and the request id due to be sent.
     * Requests can be resent if there is no response and the configurable resendWindow has been surpased.
     * @param cryptoState State object
     * @param instant Current time
     * @param config Flow config
     * @return Updated CryptoState object and the request to be sent. Request is null if no request to be sent.
     */
    fun getMessageToSend(cryptoState: CryptoState, instant: Instant, config: SmartConfig) : Pair<CryptoState, FlowOpsRequest?>
}