package net.corda.membership.service.impl

import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.membership.async.request.MembershipAsyncRequest
import net.corda.data.membership.async.request.MembershipAsyncRequestState
import net.corda.data.membership.async.request.RegistrationAction
import net.corda.data.membership.async.request.RegistrationAsyncRequest
import net.corda.data.membership.common.RegistrationStatus
import net.corda.membership.lib.registration.RegistrationRequestStatus
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.persistence.client.MembershipQueryResult
import net.corda.membership.registration.InvalidMembershipRegistrationException
import net.corda.membership.registration.NotReadyMembershipRegistrationException
import net.corda.membership.registration.RegistrationProxy
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import net.corda.test.util.time.TestClock
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.ShortHash
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID

class MemberOpsAsyncProcessorTest {
    private companion object {
        const val FAILURE_REASON = "oops"
    }

    private val shortHash = ShortHash.of("123123123123")
    private val identity = mock<HoldingIdentity>()
    private val info = mock<VirtualNodeInfo> {
        on { holdingIdentity } doReturn identity
    }
    private val registrationProxy = mock<RegistrationProxy>()
    private val virtualNodeInfoReadService = mock<VirtualNodeInfoReadService> {
        on { getByHoldingIdentityShortHash(shortHash) } doReturn info
    }
    private val membershipPersistenceClient = mock<MembershipPersistenceClient>()
    private val membershipQueryClient = mock<MembershipQueryClient> {
        on {
            queryRegistrationRequestStatus(
                any(),
                any(),
            )
        } doReturn MembershipQueryResult.Success(null)
    }
    private val clock = TestClock(
        Instant.ofEpochMilli(4000)
    )

    private val processor = MemberOpsAsyncProcessor(
        registrationProxy,
        virtualNodeInfoReadService,
        membershipPersistenceClient,
        membershipQueryClient,
        clock,
    )

    @Nested
    inner class OnNextTests {
        @Test
        fun `it with successful registration will clear the state`() {
            val id = UUID(0, 1)
            val reply = processor.onNext(
                null,
                Record(
                    "topic",
                    "key",
                    MembershipAsyncRequest(
                        RegistrationAsyncRequest(
                            shortHash.value,
                            id.toString(),
                            RegistrationAction.REQUEST_JOIN,
                            KeyValuePairList(
                                listOf(
                                    KeyValuePair(
                                        "key",
                                        "value"
                                    )
                                )
                            )
                        )
                    ),
                )
            )

            assertThat(reply).isEqualTo(
                StateAndEventProcessor.Response(
                    updatedState = null,
                    responseEvents = emptyList(),
                    markForDLQ = false,
                )
            )
        }

        @Test
        fun `it with a command that can not be replayed will raise the DLQ flag`() {
            val reply = processor.onNext(
                null,
                Record(
                    "topic",
                    "key",
                    MembershipAsyncRequest(null),
                )
            )

            assertThat(reply).isEqualTo(
                StateAndEventProcessor.Response(
                    updatedState = null,
                    responseEvents = emptyList(),
                    markForDLQ = true,
                )
            )
        }

        @Test
        fun `it with a command that can be replayed will create a state`() {
            val id = UUID(0, 1)
            whenever(registrationProxy.register(any(), any(), any()))
                .doThrow(NotReadyMembershipRegistrationException("oops"))
            val command = MembershipAsyncRequest(
                RegistrationAsyncRequest(
                    shortHash.value,
                    id.toString(),
                    RegistrationAction.REQUEST_JOIN,
                    KeyValuePairList(
                        listOf(
                            KeyValuePair(
                                "key",
                                "value"
                            )
                        )
                    )
                )
            )

            val reply = processor.onNext(
                null,
                Record(
                    "topic",
                    "key",
                    command,
                )
            )

            assertThat(reply).isEqualTo(
                StateAndEventProcessor.Response(
                    updatedState = MembershipAsyncRequestState(
                        command,
                        1,
                        clock.instant(),
                    ),
                    responseEvents = emptyList(),
                    markForDLQ = false,
                )
            )
        }

        @Test
        fun `it should increment the retry count if it fails again`() {
            val id = UUID(0, 1)
            whenever(registrationProxy.register(any(), any(), any()))
                .doThrow(NotReadyMembershipRegistrationException("oops"))
            val command = MembershipAsyncRequest(
                RegistrationAsyncRequest(
                    shortHash.value,
                    id.toString(),
                    RegistrationAction.REQUEST_JOIN,
                    KeyValuePairList(
                        listOf(
                            KeyValuePair(
                                "key",
                                "value"
                            )
                        )
                    )
                )
            )

            val reply = processor.onNext(
                MembershipAsyncRequestState(
                    command,
                    4,
                    Instant.ofEpochMilli(4000),
                ),
                Record(
                    "topic",
                    "key",
                    command,
                )
            )

            assertThat(reply).isEqualTo(
                StateAndEventProcessor.Response(
                    updatedState = MembershipAsyncRequestState(
                        command,
                        5,
                        clock.instant(),
                    ),
                    responseEvents = emptyList(),
                    markForDLQ = false,
                )
            )
        }

        @Test
        fun `it should not retry if it tried too many times`() {
            val id = UUID(0, 1)
            whenever(registrationProxy.register(any(), any(), any()))
                .doThrow(NotReadyMembershipRegistrationException("oops"))
            val command = MembershipAsyncRequest(
                RegistrationAsyncRequest(
                    shortHash.value,
                    id.toString(),
                    RegistrationAction.REQUEST_JOIN,
                    KeyValuePairList(
                        listOf(
                            KeyValuePair(
                                "key",
                                "value"
                            )
                        )
                    )
                )
            )

            val reply = processor.onNext(
                MembershipAsyncRequestState(
                    command,
                    20,
                    Instant.ofEpochMilli(4000),
                ),
                Record(
                    "topic",
                    "key",
                    command,
                )
            )

            assertThat(reply).isEqualTo(
                StateAndEventProcessor.Response(
                    updatedState = null,
                    responseEvents = emptyList(),
                    markForDLQ = true,
                )
            )
        }
    }

    @Nested
    inner class RegisterTest {
        @Test
        fun `it should call the registration proxy`() {
            val id = UUID(0, 1)
            processor.onNext(
                null,
                Record(
                    "topic",
                    "key",
                    MembershipAsyncRequest(
                        RegistrationAsyncRequest(
                            shortHash.value,
                            id.toString(),
                            RegistrationAction.REQUEST_JOIN,
                            KeyValuePairList(
                                listOf(
                                    KeyValuePair(
                                        "key",
                                        "value"
                                    )
                                )
                            )
                        )
                    ),
                )
            )

            verify(registrationProxy).register(id, identity, mapOf("key" to "value"))
        }

        @Test
        fun `it should retry if the node was not loaded yet`() {
            val id = UUID(0, 1)
            val reply = processor.onNext(
                null,
                Record(
                    "topic",
                    "key",
                    MembershipAsyncRequest(
                        RegistrationAsyncRequest(
                            "223123123123",
                            id.toString(),
                            RegistrationAction.REQUEST_JOIN,
                            KeyValuePairList(
                                listOf(
                                    KeyValuePair(
                                        "key",
                                        "value"
                                    )
                                )
                            )
                        )
                    ),
                )
            )

            assertThat(reply.updatedState).isNotNull
        }

        @Test
        fun `it should not retry if the request id is not UUID`() {
            val reply = processor.onNext(
                null,
                Record(
                    "topic",
                    "key",
                    MembershipAsyncRequest(
                        RegistrationAsyncRequest(
                            shortHash.value,
                            "nop",
                            RegistrationAction.REQUEST_JOIN,
                            KeyValuePairList(
                                listOf(
                                    KeyValuePair(
                                        "key",
                                        "value"
                                    )
                                )
                            )
                        )
                    ),
                )
            )

            assertThat(reply.markForDLQ).isTrue
        }

        @Test
        fun `it should retry if the current status is not available`() {
            whenever(membershipQueryClient.queryRegistrationRequestStatus(any(), any())).doReturn(
                MembershipQueryResult.Failure(
                    "oops"
                )
            )
            val id = UUID(0, 1)
            val reply = processor.onNext(
                null,
                Record(
                    "topic",
                    "key",
                    MembershipAsyncRequest(
                        RegistrationAsyncRequest(
                            shortHash.value,
                            id.toString(),
                            RegistrationAction.REQUEST_JOIN,
                            KeyValuePairList(
                                listOf(
                                    KeyValuePair(
                                        "key",
                                        "value"
                                    )
                                )
                            )
                        )
                    ),
                )
            )

            assertThat(reply.updatedState).isNotNull
        }

        @Test
        fun `it should retry do nothing if the status is not new any more`() {
            whenever(membershipQueryClient.queryRegistrationRequestStatus(any(), any())).doReturn(
                MembershipQueryResult.Success(
                    RegistrationRequestStatus(
                        RegistrationStatus.SENT_TO_MGM,
                        "",
                        mock(),
                        Instant.MIN,
                        Instant.MIN,
                        0
                    )
                )
            )
            val id = UUID(0, 1)
            val reply = processor.onNext(
                null,
                Record(
                    "topic",
                    "key",
                    MembershipAsyncRequest(
                        RegistrationAsyncRequest(
                            shortHash.value,
                            id.toString(),
                            RegistrationAction.REQUEST_JOIN,
                            KeyValuePairList(
                                listOf(
                                    KeyValuePair(
                                        "key",
                                        "value"
                                    )
                                )
                            )
                        )
                    ),
                )
            )

            assertThat(reply.updatedState).isNull()
            assertThat(reply.markForDLQ).isFalse
            verifyNoInteractions(registrationProxy)
        }

        @Test
        fun `it should register if the status is new`() {
            whenever(membershipQueryClient.queryRegistrationRequestStatus(any(), any())).doReturn(
                MembershipQueryResult.Success(
                    RegistrationRequestStatus(
                        RegistrationStatus.NEW,
                        "",
                        mock(),
                        Instant.MIN,
                        Instant.MIN,
                        0
                    )
                )
            )
            val id = UUID(0, 1)
            processor.onNext(
                null,
                Record(
                    "topic",
                    "key",
                    MembershipAsyncRequest(
                        RegistrationAsyncRequest(
                            shortHash.value,
                            id.toString(),
                            RegistrationAction.REQUEST_JOIN,
                            KeyValuePairList(
                                listOf(
                                    KeyValuePair(
                                        "key",
                                        "value"
                                    )
                                )
                            )
                        )
                    ),
                )
            )

            verify(registrationProxy).register(id, identity, mapOf("key" to "value"))
        }

        @Test
        fun `it should not retry if the request id invalid`() {
            val id = UUID(0, 1)
            whenever(registrationProxy.register(any(), any(), any()))
                .doThrow(InvalidMembershipRegistrationException("oops"))
            val reply = processor.onNext(
                null,
                Record(
                    "topic",
                    "key",
                    MembershipAsyncRequest(
                        RegistrationAsyncRequest(
                            shortHash.value,
                            id.toString(),
                            RegistrationAction.REQUEST_JOIN,
                            KeyValuePairList(
                                listOf(
                                    KeyValuePair(
                                        "key",
                                        "value"
                                    )
                                )
                            )
                        )
                    ),
                )
            )

            assertThat(reply.markForDLQ).isTrue
        }
    }
}
