package net.corda.p2p.linkmanager.tracker

import net.corda.data.identity.HoldingIdentity
import net.corda.data.p2p.app.AuthenticatedMessageHeader
import net.corda.messaging.api.records.Record
import net.corda.p2p.linkmanager.delivery.ReplayScheduler
import net.corda.p2p.linkmanager.sessions.SessionManager
import net.corda.v5.base.types.MemberX500Name
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import net.corda.virtualnode.HoldingIdentity as CordaHoldingIdentity

class AckProcessorTest {
    private val messageId = "messageId"
    val mockHeader = mock<AuthenticatedMessageHeader> {
        on { source } doReturn HoldingIdentity(
            "CN=Alice, O=Alice Corp, L=LDN, C=GB",
            "group",
        )
        on { destination } doReturn HoldingIdentity(
            "CN=Bob, O=Bob Corp, L=LDN, C=GB",
            "group",
        )
    }
    private val record = MessageRecord(
        message = mock {
            on { header } doReturn mockHeader
        },
        partition = 20,
        offset = 1020,
    )
    private val partitionsStates = mock<PartitionsStates>()
    private val cache = mock<DataMessageCache> {
        on { remove(messageId) } doReturn record
    }
    private val replayScheduler = mock<ReplayScheduler<SessionManager.Counterparties, String>>()

    private val processor = AckProcessor(
        partitionsStates = partitionsStates,
        cache = cache,
        replayScheduler = replayScheduler,
    )

    @Test
    fun `unknown message will return a completed future`() {
        val future = processor.onNext(
            Record(
                "topic",
                "unknown-key",
                "value",
            ),
        )

        assertThat(future).isDone
    }

    @Test
    fun `unknown message will not interact with the states`() {
        processor.onNext(
            Record(
                "topic",
                "unknown-key",
                "value",
            ),
        )

        verifyNoInteractions(partitionsStates)
    }

    @Test
    fun `unknown message will not interact with the scheduler`() {
        processor.onNext(
            Record(
                "topic",
                "unknown-key",
                "value",
            ),
        )

        verifyNoInteractions(replayScheduler)
    }

    @Test
    fun `known message will not tell the states to forget it`() {
        processor.onNext(
            Record(
                "topic",
                messageId,
                "value",
            ),
        )

        verify(partitionsStates).forget(record)
    }

    @Test
    fun `known message will remove it from the scheduler`() {
        processor.onNext(
            Record(
                "topic",
                messageId,
                "value",
            ),
        )

        verify(replayScheduler).removeFromReplay(
            messageId,
            SessionManager.Counterparties(
                CordaHoldingIdentity(
                    MemberX500Name.parse("CN=Alice, O=Alice Corp, L=LDN, C=GB"),
                    "group",
                ),
                CordaHoldingIdentity(
                    MemberX500Name.parse("CN=Bob, O=Bob Corp, L=LDN, C=GB"),
                    "group",
                ),
            ),
        )
    }

    @Test
    fun `known message will return a completed future`() {
        val future = processor.onNext(
            Record(
                "topic",
                messageId,
                "value",
            ),
        )

        assertThat(future).isDone
    }
}
