package net.corda.membership.service.impl

import net.corda.crypto.core.ShortHash
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.membership.async.request.MembershipAsyncRequest
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
import net.corda.messaging.api.records.Record
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
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

    private val processor = MemberOpsAsyncProcessor(
        registrationProxy,
        virtualNodeInfoReadService,
        membershipPersistenceClient,
        membershipQueryClient,
    )

    @Test
    fun `onNext will ignore null value`() {
        assertDoesNotThrow {
            processor.onNext(
                listOf(
                    Record(
                        "topic",
                        "key",
                        null,
                    )
                )
            )
        }
    }

    @Test
    fun `onNext will ignore null requests`() {
        assertDoesNotThrow {
            processor.onNext(
                listOf(
                    Record(
                        "topic",
                        "key",
                        MembershipAsyncRequest(null),
                    )
                )
            )
        }
    }

    @Test
    fun `onNext will send request to the proxy`() {
        val id = UUID(0, 1)
        processor.onNext(
            listOf(
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
        )

        verify(registrationProxy).register(id, identity, mapOf("key" to "value"))
    }

    @Test
    fun `onNext with new request will call the proxy`() {
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
            listOf(
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
        )

        verify(registrationProxy).register(id, identity, mapOf("key" to "value"))
    }

    @Test
    fun `onNext with non-new request will be ignored`() {
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
        processor.onNext(
            listOf(
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
        )

        verifyNoInteractions(registrationProxy)
    }

    @Test
    fun `onNext when the request is not available will be ignored`() {
        whenever(membershipQueryClient.queryRegistrationRequestStatus(any(), any())).doReturn(
            MembershipQueryResult.Failure(
                "oops"
            )
        )
        val id = UUID(0, 1)
        processor.onNext(
            listOf(
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
        )

        verifyNoInteractions(registrationProxy)
    }

    @Test
    fun `onNext with invalid id will throw an exception`() {
        whenever(virtualNodeInfoReadService.getByHoldingIdentityShortHash(any())).doReturn(null)
        val id = UUID(0, 1)
        assertDoesNotThrow {
            processor.onNext(
                listOf(
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
            )
        }
    }

    @Test
    fun `onNext with invalid uuid will do nothing`() {
        processor.onNext(
            listOf(
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
        )

        verifyNoInteractions(registrationProxy)
        verifyNoInteractions(membershipPersistenceClient)
    }

    @Test
    fun `onNext with invalid request will persist the invalid state`() {
        val id = UUID(0, 1)
        whenever(registrationProxy.register(any(), any(), any()))
            .doThrow(InvalidMembershipRegistrationException("oops"))

        processor.onNext(
            listOf(
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
        )

        verify(membershipPersistenceClient).setRegistrationRequestStatus(
            identity,
            id.toString(),
            RegistrationStatus.INVALID,
        )
    }

    @Test
    fun `onNext with not ready error will not throw an exception`() {
        val id = UUID(0, 1)
        whenever(registrationProxy.register(any(), any(), any()))
            .doThrow(NotReadyMembershipRegistrationException("oops"))

        assertDoesNotThrow {
            processor.onNext(
                listOf(
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
            )
        }
    }
}
