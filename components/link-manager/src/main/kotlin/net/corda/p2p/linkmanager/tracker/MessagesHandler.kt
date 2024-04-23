package net.corda.p2p.linkmanager.tracker

import net.corda.data.p2p.app.AppMessage
import net.corda.data.p2p.app.AuthenticatedMessage
import net.corda.messaging.api.records.EventLogRecord

internal class MessagesHandler(
    private val partitionsStates: PartitionsStates,
    private val cache: DataMessageCache,
) {
    fun handleMessagesAndFilterRecords(
        events: List<EventLogRecord<String, AppMessage>>,
    ): List<EventLogRecord<String, AppMessage>> {
        val authenticatedMessages = events.mapNotNull {
            val message = it.value?.message as? AuthenticatedMessage
            if (message != null) {
                MessageRecord(
                    message = message,
                    offset = it.offset,
                    partition = it.partition,
                )
            } else {
                null
            }
        }
        cache.put(authenticatedMessages)
        partitionsStates.read(authenticatedMessages)

        return partitionsStates.getEventToProcess(events)
    }

    fun handled(
        events: Collection<EventLogRecord<String, AppMessage>>,
    ) {
        partitionsStates.handled(events)
    }
}
