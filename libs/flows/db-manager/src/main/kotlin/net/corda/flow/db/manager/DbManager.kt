package net.corda.flow.db.manager

import java.time.Instant
import net.corda.data.flow.state.db.Query
import net.corda.data.persistence.EntityRequest
import net.corda.data.persistence.EntityResponse
import net.corda.libs.configuration.SmartConfig

interface DbManager {

    /**
     * Generate a Query object to represent a DB request that needs to be sent
     * @param requestId Unique ID for this request.
     * @param request Request to be sent
     * @return Query object with request stored and ready to be sent
     */
    fun processMessageToSend(requestId: String, request: EntityRequest) : Query

    /**
     * Process a db response. Compare to the query objects request. If the response matches the request, store the response in the query.
     * @param query Query object representing the last sent request
     * @param response Response received
     * @return Updated Query object. If the response matches the request the response is saved to the query object.
     */
    fun processMessageReceived(query: Query, response: EntityResponse) : Query


    fun getMessageToSend(query: Query, instant: Instant, config: SmartConfig) : Pair<Query, EntityRequest?>
}
