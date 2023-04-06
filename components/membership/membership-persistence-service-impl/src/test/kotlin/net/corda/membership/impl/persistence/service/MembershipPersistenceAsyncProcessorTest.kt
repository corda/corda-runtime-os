package net.corda.membership.impl.persistence.service

import net.corda.data.identity.HoldingIdentity
import net.corda.data.membership.db.request.MembershipPersistenceRequest
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.async.MembershipPersistenceAsyncRequest
import net.corda.data.membership.db.request.async.MembershipPersistenceAsyncRequestState
import net.corda.membership.impl.persistence.service.handler.HandlerFactories
import net.corda.membership.impl.persistence.service.handler.PersistenceHandlerServices
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import net.corda.utilities.time.Clock
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import javax.persistence.OptimisticLockException
import javax.persistence.PessimisticLockException

class MembershipPersistenceAsyncProcessorTest {
    private val requestContext = MembershipRequestContext(
        Instant.ofEpochMilli(1002),
        "requestId",
        HoldingIdentity(
            "name",
            "group",
        )
    )
    private val request = MembershipPersistenceRequest(requestContext, 50)
    private val envelope = MembershipPersistenceAsyncRequest(
        request
    )
    private val now = Instant.ofEpochMilli(2001)
    private val clock = mock<Clock> {
        on { instant() } doReturn now
    }
    private val persistenceHandlerServices = mock<PersistenceHandlerServices> {
        on { clock } doReturn clock
    }
    private val handlers = mock<HandlerFactories> {
        on { handle(request) } doReturn 4
        on { persistenceHandlerServices } doReturn persistenceHandlerServices
    }
    private val processor = MembershipPersistenceAsyncProcessor(
        handlers
    )

    @Test
    fun `null request mark for DLQ`() {
        val reply = processor.onNext(
            null,
            Record("topic", "key", null)
        )

        assertThat(reply).isEqualTo(
            StateAndEventProcessor.Response(
                null,
                emptyList(),
                true,
            )
        )
    }

    @Test
    fun `successful request will empty the state and clear the DLQ`() {
        val reply = processor.onNext(
            null,
            Record("topic", "key", envelope)
        )

        assertThat(reply).isEqualTo(
            StateAndEventProcessor.Response(
                null,
                emptyList(),
                false,
            )
        )
    }

    @Test
    fun `request will call the handlers`() {
        processor.onNext(
            null,
            Record("topic", "key", envelope)
        )

        verify(handlers).handle(request)
    }

    @Test
    fun `error will mark for DLQ`() {
        whenever(handlers.handle(any())).doThrow(CordaRuntimeException("Nop"))

        val reply = processor.onNext(
            null,
            Record("topic", "key", envelope)
        )

        assertThat(reply).isEqualTo(
            StateAndEventProcessor.Response(
                null,
                emptyList(),
                true,
            )
        )
    }

    @Test
    fun `first retry will set the state`() {
        whenever(handlers.handle(any())).doThrow(PessimisticLockException("Nop"))

        val reply = processor.onNext(
            null,
            Record("topic", "key", envelope)
        )

        assertThat(reply).isEqualTo(
            StateAndEventProcessor.Response(
                MembershipPersistenceAsyncRequestState(
                    envelope,
                    1,
                    now,
                ),
                emptyList(),
                false,
            )
        )
    }

    @Test
    fun `second retry will update the state`() {
        whenever(handlers.handle(any())).doThrow(OptimisticLockException("Nop"))

        val reply = processor.onNext(
            MembershipPersistenceAsyncRequestState(
                envelope,
                1,
                now,
            ),
            Record("topic", "key", envelope)
        )

        assertThat(reply).isEqualTo(
            StateAndEventProcessor.Response(
                MembershipPersistenceAsyncRequestState(
                    envelope,
                    2,
                    now,
                ),
                emptyList(),
                false,
            )
        )
    }

    @Test
    fun `last retry will set the DLQ flag`() {
        whenever(handlers.handle(any())).doThrow(OptimisticLockException("Nop"))

        val reply = processor.onNext(
            MembershipPersistenceAsyncRequestState(
                envelope,
                20,
                now,
            ),
            Record("topic", "key", envelope)
        )

        assertThat(reply).isEqualTo(
            StateAndEventProcessor.Response(
                null,
                emptyList(),
                true,
            )
        )
    }
}
