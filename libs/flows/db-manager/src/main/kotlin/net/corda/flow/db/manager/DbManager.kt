package net.corda.flow.db.manager

import java.time.Instant
import net.corda.data.flow.state.db.Query
import net.corda.data.persistence.EntityRequest
import net.corda.data.persistence.EntityResponse
import net.corda.libs.configuration.SmartConfig

interface DbManager {
    fun processMessageToSend(requestId: String, request: EntityRequest) : Query

    fun processMessageReceived(query: Query, response: EntityResponse) : Query

    fun getMessageToSend(query: Query, instant: Instant, config: SmartConfig) : Pair<Query, EntityRequest?>
}
