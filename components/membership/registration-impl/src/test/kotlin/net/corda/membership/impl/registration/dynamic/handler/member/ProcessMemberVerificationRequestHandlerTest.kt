package net.corda.membership.impl.registration.dynamic.handler.member

import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.data.membership.command.registration.member.ProcessMemberVerificationRequest
import net.corda.data.membership.common.RegistrationStatus
import net.corda.data.membership.p2p.VerificationRequest
import net.corda.data.membership.p2p.VerificationResponse
import net.corda.membership.impl.registration.VerificationResponseKeys
import net.corda.membership.impl.registration.dynamic.handler.MemberTypeChecker
import net.corda.membership.p2p.helpers.P2pRecordsFactory
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.messaging.api.records.Record
import net.corda.data.p2p.app.AppMessage
import net.corda.membership.persistence.client.MembershipPersistenceOperation
import net.corda.data.p2p.app.MembershipStatusFilter
import net.corda.test.util.identity.createTestHoldingIdentity
import net.corda.test.util.time.TestClock
import net.corda.virtualnode.toAvro
import net.corda.virtualnode.toCorda
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

class ProcessMemberVerificationRequestHandlerTest {
    private companion object {
        const val GROUP_ID = "ABC123"
        const val REGISTRATION_ID = "REG-01"

        val clock = TestClock(Instant.ofEpochSecond(0))
    }

    private val mgm = createTestHoldingIdentity("C=GB, L=London, O=MGM", GROUP_ID).toAvro()
    private val member = createTestHoldingIdentity("C=GB, L=London, O=Alice", GROUP_ID).toAvro()
    private val requestBody = KeyValuePairList(listOf(KeyValuePair("KEY", "dummyKey")))
    private val verificationRequest = VerificationRequest(
        REGISTRATION_ID,
        requestBody
    )

    private val response = argumentCaptor<VerificationResponse>()
    private val cordaAvroSerializationFactory = mock<CordaAvroSerializationFactory>()
    private val records = Record(
        "topic",
        "key",
        false,
    )
    private val operation = mock<MembershipPersistenceOperation<Unit>> {
        on { createAsyncCommands() } doReturn listOf(records)
    }
    private val membershipPersistenceClient = mock<MembershipPersistenceClient> {
        on {
            setRegistrationRequestStatus(
                member.toCorda(),
                REGISTRATION_ID,
                RegistrationStatus.PENDING_MEMBER_VERIFICATION,
            )
        } doReturn operation
    }
    private val memberTypeChecker = mock<MemberTypeChecker>()
    private val p2pMessage = mock<Record<String, AppMessage>>()
    private val p2pRecordsFactory = mock<P2pRecordsFactory> {
        on {
            createAuthenticatedMessageRecord(
                eq(member),
                eq(mgm),
                response.capture(),
                isNull(),
                anyOrNull(),
                eq(MembershipStatusFilter.ACTIVE)
            )
        } doReturn p2pMessage
    }

    private val processMemberVerificationRequestHandler = ProcessMemberVerificationRequestHandler(
        clock,
        cordaAvroSerializationFactory,
        membershipPersistenceClient,
        memberTypeChecker,
        p2pRecordsFactory,
    )

    @Test
    fun `handler returns response message`() {
        val result = processMemberVerificationRequestHandler.invoke(
            null,
            Record(
                "dummyTopic",
                member.toString(),
                RegistrationCommand(
                    ProcessMemberVerificationRequest(member, mgm, verificationRequest)
                )
            )
        )
        assertThat(result.outputStates).hasSize(2)
            .contains(p2pMessage)
            .contains(records)
        assertThat(result.updatedState).isNull()
        with(response.firstValue) {
            assertThat(this.registrationId).isEqualTo(REGISTRATION_ID)
            assertThat(this.payload.items).hasSize(1)
                .anySatisfy {
                    assertThat(it.key).isEqualTo(VerificationResponseKeys.VERIFIED)
                    assertThat(it.value).isEqualTo("true")
                }
        }
    }

    @Test
    fun `handler invalidate if the member type is not a member`() {
        whenever(memberTypeChecker.isMgm(member)).doReturn(true)
        processMemberVerificationRequestHandler.invoke(
            null,
            Record(
                "dummyTopic",
                member.toString(),
                RegistrationCommand(
                    ProcessMemberVerificationRequest(member, mgm, verificationRequest)
                )
            )
        )
        assertThat(response.firstValue.payload.items)
            .anySatisfy {
                assertThat(it.key).isEqualTo(VerificationResponseKeys.VERIFIED)
                assertThat(it.value).isEqualTo("false")
            }
            .anySatisfy {
                assertThat(it.key).isEqualTo(VerificationResponseKeys.FAILURE_REASONS)
                assertThat(it.value).isNotBlank()
            }
    }

    @Test
    fun `handler persist the request status`() {
        processMemberVerificationRequestHandler.invoke(
            null,
            Record(
                "dummyTopic",
                member.toString(),
                RegistrationCommand(
                    ProcessMemberVerificationRequest(member, mgm, verificationRequest)
                )
            )
        )

        verify(membershipPersistenceClient).setRegistrationRequestStatus(
            member.toCorda(),
            REGISTRATION_ID,
            RegistrationStatus.PENDING_MEMBER_VERIFICATION
        )
    }
}
