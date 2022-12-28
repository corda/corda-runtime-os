package net.corda.p2p.linkmanager.sessions

import net.corda.lifecycle.domino.logic.DominoTile
import net.corda.lifecycle.domino.logic.util.PublisherWithDominoLogic
import net.corda.messaging.api.records.Record
import net.corda.p2p.AuthenticatedMessageAndKey
import net.corda.p2p.LinkOutMessage
import net.corda.p2p.app.AuthenticatedMessage
import net.corda.p2p.app.AuthenticatedMessageHeader
import net.corda.p2p.crypto.protocol.api.AuthenticatedSession
import net.corda.p2p.crypto.protocol.api.AuthenticationResult
import net.corda.test.util.identity.createTestHoldingIdentity
import net.corda.virtualnode.toAvro
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mockConstruction
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer

class PendingSessionMessageQueuesImplTest {

    private val publishedRecords = argumentCaptor<List<Record<String, LinkOutMessage>>>()
    private val mac = mock< AuthenticationResult> {
        on { mac } doReturn "mac".toByteArray()
    }
    private val session = mock<AuthenticatedSession> {
        on { sessionId } doReturn "SessionId"
        on { createMac(any()) } doReturn mac
    }
    private val recordForSessionEstablished = Record("topic", "key", mock<LinkOutMessage>())
    private val sessionManager = mock<SessionManager> {
        on { recordsForSessionEstablished(any(), any()) } doReturn listOf(recordForSessionEstablished)
    }
    private val publisherWithDominoLogic = mockConstruction(PublisherWithDominoLogic::class.java) { mock, _ ->
        whenever(mock.isRunning).doReturn(true)
        @Suppress("UNCHECKED_CAST")
        whenever(mock.withLifecycleLock(any<() -> Any>())).thenAnswer{ (it.arguments.first() as () -> Any).invoke() }
        val dominoTile = mock<DominoTile> {
            on { isRunning } doReturn true
        }
        whenever(mock.dominoTile).doReturn(dominoTile)
        whenever(mock.publish(publishedRecords.capture())).doReturn(emptyList())
    }
    private val sessionCounterparties = SessionManager.SessionCounterparties(
        createTestHoldingIdentity("CN=Carol, O=Corp, L=LDN, C=GB", "group-1"),
        createTestHoldingIdentity("CN=David, O=Corp, L=LDN, C=GB", "group-2")
    )

    private val queue = PendingSessionMessageQueuesImpl(mock(), mock(), mock())

    @AfterEach
    fun cleanUp() {
        publisherWithDominoLogic.close()
    }

    @Test
    fun `sessionNegotiatedCallback publish messages`() {
        (1..5).map {
            val header = AuthenticatedMessageHeader(
                sessionCounterparties.counterpartyId.toAvro(),
                sessionCounterparties.ourId.toAvro(),
                null,
                "msg-$it",
                "",
                "system-1"
            )
            val data = ByteBuffer.wrap("$it".toByteArray())
            AuthenticatedMessageAndKey(AuthenticatedMessage(header, data), "key")
        }.onEach {
            queue.queueMessage(it)
        }

        queue.sessionNegotiatedCallback(sessionManager, sessionCounterparties, session)

        assertThat(
            publishedRecords.firstValue
        ).contains(recordForSessionEstablished)
    }

    @Test
    fun `sessionNegotiatedCallback calls recordsForSessionEstablished`() {
        val count = 3
        val messages = (1..count).map {
            val header = AuthenticatedMessageHeader(
                sessionCounterparties.counterpartyId.toAvro(),
                sessionCounterparties.ourId.toAvro(),
                null,
                "msg-$it",
                "",
                "system-1"
            )
            val data = ByteBuffer.wrap("$it".toByteArray())
            AuthenticatedMessageAndKey(AuthenticatedMessage(header, data), "key")
        }
        messages.onEach {
            queue.queueMessage(it)
        }

        queue.sessionNegotiatedCallback(sessionManager, sessionCounterparties, session)

        messages.forEach {
            verify(sessionManager).recordsForSessionEstablished(session, it)
        }
    }

    @Test
    fun `sessionNegotiatedCallback will not publish messages for another counter parties`() {
        (1..5).map {
            val header = AuthenticatedMessageHeader(
                sessionCounterparties.counterpartyId.toAvro(),
                sessionCounterparties.ourId.toAvro(),
                null,
                "msg-$it",
                "",
                "system-1"
            )
            val data = ByteBuffer.wrap("$it".toByteArray())
            AuthenticatedMessageAndKey(AuthenticatedMessage(header, data), "key")
        }.onEach {
            queue.queueMessage(it)
        }

        val anotherSessionCounterparties = SessionManager.SessionCounterparties(
            createTestHoldingIdentity("CN=Carol, O=Corp, L=LDN, C=GB", "group-2"),
            createTestHoldingIdentity("CN=David, O=Corp, L=LDN, C=GB", "group-1")
        )
        queue.sessionNegotiatedCallback(sessionManager, anotherSessionCounterparties, session)

        assertThat(publishedRecords.allValues).isEmpty()
    }
}
