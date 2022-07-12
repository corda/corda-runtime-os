package net.corda.flow.persistence.manager

import java.time.Instant
import net.corda.data.flow.state.persistence.PersistenceState
import net.corda.data.persistence.EntityRequest
import net.corda.data.persistence.EntityResponse
import net.corda.libs.configuration.SmartConfig

interface PersistenceManager {

    /**
     * Generate a state object to represent a persistence request that needs to be sent
     * @param requestId Unique ID for this request.
     * @param request Request to be sent
     * @return PersistenceState object with request stored and ready to be sent
     */
    fun processMessageToSend(requestId: String, request: EntityRequest) : PersistenceState

    /**
     * Process a persistence response. Compare to the persistenceState object's request. If the response matches the request,
     * store the response in the persistenceState.
     * @param persistenceState object to represent a persistence query
     * @param response Response received
     * @return Updated persistenceState object. If the response matches the request the response is saved to the object.
     */
    fun processMessageReceived(persistenceState: PersistenceState, response: EntityResponse) : PersistenceState

    /**
     * Checks the persistenceState to see if there is a request to send.
     * Request will be returned if the request is not null and the request is due to be sent.
     * Requests can be resent if there is no response and the configurable resendWindow has been surpassed.
     * @param persistenceState object to represent a persistence query
     * @param instant Current time
     * @param config Flow config
     * @return Updated persistenceState object and the request to be sent. Request is null if no request to be sent.
     */
    fun getMessageToSend(persistenceState: PersistenceState, instant: Instant, config: SmartConfig) : Pair<PersistenceState, EntityRequest?>
}
