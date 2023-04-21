package net.corda.external.messaging.services

import net.corda.libs.external.messaging.entities.Route
import net.corda.messaging.api.records.Record

interface ExternalMessagingRecordFactory {

    fun createSendRecord(
        holdingId: String,
        route: Route,
        messageId: String,
        message: String
    ): Record<String, String>
}
