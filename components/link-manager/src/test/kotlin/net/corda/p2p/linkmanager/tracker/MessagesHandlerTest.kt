package net.corda.p2p.linkmanager.tracker

import net.corda.data.p2p.app.AppMessage
import net.corda.data.p2p.app.AuthenticatedMessage
import net.corda.messaging.api.records.EventLogRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class MessagesHandlerTest {
    private val messageOne = mock<AuthenticatedMessage>()
    private val messageTwo = mock<AuthenticatedMessage>()
    private val events: List<EventLogRecord<String, AppMessage>> = listOf(
        EventLogRecord(
            topic = "topic",
            key = "key1",
            value = mock<AppMessage> {
                on { message } doReturn messageOne
            },
            offset = 100,
            partition = 40,
        ),
        EventLogRecord(
            topic = "topic",
            key = "key2",
            value = mock<AppMessage> {},
            offset = 101,
            partition = 30,
        ),
        EventLogRecord(
            topic = "topic",
            key = "key",
            value = mock<AppMessage> {
                on { message } doReturn messageTwo
            },
            offset = 102,
            partition = 30,
        ),
        EventLogRecord(
            topic = "topic",
            key = "key",
            value = null,
            offset = 102,
            partition = 30,
        ),
    )
    private val records: List<EventLogRecord<String, AppMessage>> = listOf(
        EventLogRecord(
            topic = "topic",
            key = "key1",
            value = mock<AppMessage> { },
            offset = 100,
            partition = 30,
        ),
        EventLogRecord(
            topic = "topic",
            key = "key2",
            value = null,
            offset = 101,
            partition = 30,
        ),
    )
    private val partitionsStates = mock<PartitionsStates> {
        on { getEventToProcess(events) } doReturn records
    }
    private val cache = mock<DataMessageCache> {}
    private val handler = MessagesHandler(partitionsStates, cache)

    @Test
    fun `handleMessagesAndFilterRecords sends only the AuthenticatedMessage to the cache`() {
        handler.handleMessagesAndFilterRecords(events)

        verify(cache).put(
            listOf(
                MessageRecord(
                    message = messageOne,
                    offset = 100,
                    partition = 40,
                ),
                MessageRecord(
                    message = messageTwo,
                    offset = 102,
                    partition = 30,
                ),
            ),
        )
    }

    @Test
    fun `handleMessagesAndFilterRecords sends only the AuthenticatedMessage to the states`() {
        handler.handleMessagesAndFilterRecords(events)

        verify(partitionsStates).read(
            listOf(
                MessageRecord(
                    message = messageOne,
                    offset = 100,
                    partition = 40,
                ),
                MessageRecord(
                    message = messageTwo,
                    offset = 102,
                    partition = 30,
                ),
            ),
        )
    }

    @Test
    fun `handleMessagesAndFilterRecords return the filter events`() {
        val returnedRecords = handler.handleMessagesAndFilterRecords(events)

        assertThat(returnedRecords).isEqualTo(records)
    }

    @Test
    fun `handled sends the events to the state`() {
        handler.handled(events)

        verify(partitionsStates).handled(events)
    }
}
