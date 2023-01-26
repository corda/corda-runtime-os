package net.corda.membership.impl.registration.dynamic.handler.mgm

import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.data.membership.command.registration.mgm.ApproveRegistration
import net.corda.data.membership.command.registration.mgm.DeclineRegistration
import net.corda.data.membership.command.registration.mgm.ProcessMemberVerificationResponse
import net.corda.data.membership.common.ApprovalRuleDetails
import net.corda.data.membership.common.RegistrationStatus
import net.corda.data.membership.p2p.SetOwnRegistrationStatus
import net.corda.data.membership.p2p.VerificationResponse
import net.corda.data.membership.state.RegistrationState
import net.corda.libs.configuration.SmartConfig
import net.corda.membership.impl.registration.VerificationResponseKeys
import net.corda.membership.impl.registration.dynamic.handler.MemberTypeChecker
import net.corda.membership.impl.registration.dynamic.handler.MissingRegistrationStateException
import net.corda.membership.p2p.helpers.P2pRecordsFactory
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipPersistenceResult
import net.corda.messaging.api.records.Record
import net.corda.data.p2p.app.AppMessage
import net.corda.membership.lib.registration.RegistrationRequestStatus
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.persistence.client.MembershipQueryResult
import net.corda.membership.read.MembershipGroupReader
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.schema.configuration.MembershipConfig.TtlsConfig.TTLS
import net.corda.schema.configuration.MembershipConfig.TtlsConfig.UPDATE_TO_PENDING_AUTO_APPROVAL
import net.corda.test.util.identity.createTestHoldingIdentity
import net.corda.virtualnode.toAvro
import net.corda.virtualnode.toCorda
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ProcessMemberVerificationResponseHandlerTest {
    private companion object {
        const val GROUP_ID = "ABC123"
        const val REGISTRATION_ID = "REG-01"
        const val TOPIC = "dummyTopic"
        const val APPROVAL_RULE_STRING = "^*"
        const val MEMBER_KEY = "member"
    }

    private val mgm = createTestHoldingIdentity("C=GB, L=London, O=MGM", GROUP_ID).toAvro()
    private val member = createTestHoldingIdentity("C=GB, L=London, O=Alice", GROUP_ID).toAvro()
    private val command = ProcessMemberVerificationResponse(
        VerificationResponse(
            REGISTRATION_ID,
            KeyValuePairList(
                listOf(
                    KeyValuePair(VerificationResponseKeys.VERIFIED, true.toString())
                )
            )
        )
    )
    private val state = RegistrationState(
        REGISTRATION_ID,
        member,
        mgm
    )

    private val membershipPersistenceClient = mock<MembershipPersistenceClient> {
        on {
            setRegistrationRequestStatus(
                mgm.toCorda(),
                REGISTRATION_ID,
                RegistrationStatus.PENDING_AUTO_APPROVAL
            )
        } doReturn MembershipPersistenceResult.success()
    }
    private val approvalRuleDetails = mock<ApprovalRuleDetails> {
        on { ruleRegex } doReturn APPROVAL_RULE_STRING
    }
    private val memberContext = mock<KeyValuePairList> {
        on { items } doReturn listOf(KeyValuePair(MEMBER_KEY, MEMBER_KEY))
    }
    private val requestStatus = mock<RegistrationRequestStatus> {
        on { memberContext } doReturn memberContext
    }
    private val membershipQueryClient = mock<MembershipQueryClient> {
        on { queryRegistrationRequestStatus(eq(mgm.toCorda()), any()) } doReturn MembershipQueryResult.Success(requestStatus)
    }
    private val groupReader = mock<MembershipGroupReader>()
    private val membershipGroupReaderProvider = mock<MembershipGroupReaderProvider> {
        on { getGroupReader(any()) } doReturn groupReader
    }
    private val record = mock<Record<String, AppMessage>>()
    private val capturedStatus = argumentCaptor<SetOwnRegistrationStatus>()
    private val p2pRecordsFactory = mock<P2pRecordsFactory> {
        on {
            createAuthenticatedMessageRecord(
                eq(mgm),
                eq(member),
                capturedStatus.capture(),
                any(),
                any()
            )
        } doReturn record
    }
    private val memberTypeChecker = mock<MemberTypeChecker> {
        on { isMgm(mgm) } doReturn true
        on { isMgm(member) } doReturn false
    }
    private val config = mock<SmartConfig>()

    private val processMemberVerificationResponseHandler = ProcessMemberVerificationResponseHandler(
        membershipPersistenceClient,
        mock(),
        mock(),
        memberTypeChecker,
        config,
        membershipQueryClient,
        membershipGroupReaderProvider,
        p2pRecordsFactory,
    )

    @Test
    fun `handler returns approve member command with auto-approval status`() {
        whenever(membershipQueryClient.getApprovalRules(any(), any())).doReturn(MembershipQueryResult.Success(emptyList()))
        val result = processMemberVerificationResponseHandler.invoke(state, Record(TOPIC, member.toString(), RegistrationCommand(command)))

        verify(membershipPersistenceClient, times(1)).setRegistrationRequestStatus(
            mgm.toCorda(),
            REGISTRATION_ID,
            RegistrationStatus.PENDING_AUTO_APPROVAL
        )
        assertThat(capturedStatus.firstValue.newStatus).isEqualTo(RegistrationStatus.PENDING_AUTO_APPROVAL)

        assertThat(result.outputStates).hasSize(2)
            .contains(record)
            .anyMatch {
                val key = it.key
                val value = it.value
                key == "$REGISTRATION_ID-${mgm.toCorda().shortHash}" &&
                        value is RegistrationCommand &&
                        value.command is ApproveRegistration
            }
        with(result.updatedState) {
            assertThat(this?.registeringMember).isEqualTo(member)
            assertThat(this?.mgm).isEqualTo(mgm)
            assertThat(this?.registrationId).isEqualTo(REGISTRATION_ID)
        }
    }

    @Test
    fun `handler sets request status to manual approval`() {
        whenever(membershipQueryClient.getApprovalRules(any(), any())).doReturn(MembershipQueryResult.Success(listOf(approvalRuleDetails)))
        val result = processMemberVerificationResponseHandler.invoke(state, Record(TOPIC, member.toString(), RegistrationCommand(command)))

        verify(membershipPersistenceClient).setRegistrationRequestStatus(
            mgm.toCorda(),
            REGISTRATION_ID,
            RegistrationStatus.PENDING_MANUAL_APPROVAL
        )
        assertThat(capturedStatus.firstValue.newStatus).isEqualTo(RegistrationStatus.PENDING_MANUAL_APPROVAL)

        assertThat(result.outputStates).hasSize(1)
        with(result.updatedState) {
            assertThat(this?.registeringMember).isEqualTo(member)
            assertThat(this?.mgm).isEqualTo(mgm)
            assertThat(this?.registrationId).isEqualTo(REGISTRATION_ID)
        }
    }

    @Test
    fun `handler returns decline member command if the verification failed`() {
        val command = ProcessMemberVerificationResponse(
            VerificationResponse(
                REGISTRATION_ID,
                KeyValuePairList(
                    listOf(
                        KeyValuePair(VerificationResponseKeys.VERIFIED, false.toString()),
                        KeyValuePair(VerificationResponseKeys.FAILURE_REASONS, "one"),
                        KeyValuePair(VerificationResponseKeys.FAILURE_REASONS, "two"),
                    )
                )
            )
        )
        val result = processMemberVerificationResponseHandler.invoke(state, Record(TOPIC, member.toString(), RegistrationCommand(command)))

        verify(membershipPersistenceClient, never()).setRegistrationRequestStatus(
            any(),
            any(),
            any(),
        )

        assertThat(result.outputStates).hasSize(1)
            .anyMatch {
                ((it.value as? RegistrationCommand)?.command as? DeclineRegistration)?.reason?.isNotBlank() == true
            }
    }

    @Test
    fun `handler returns decline member command if the member is an MGM`() {
        whenever(memberTypeChecker.isMgm(member)).doReturn(true)
        val result = processMemberVerificationResponseHandler.invoke(state, Record(TOPIC, member.toString(), RegistrationCommand(command)))

        verify(membershipPersistenceClient, never()).setRegistrationRequestStatus(
            any(),
            any(),
            any(),
        )

        assertThat(result.outputStates).hasSize(1)
            .anyMatch {
                ((it.value as? RegistrationCommand)?.command as? DeclineRegistration)?.reason?.isNotBlank() == true
            }
    }

    @Test
    fun `handler returns decline member command if the mgm is not an MGM`() {
        whenever(memberTypeChecker.isMgm(mgm)).doReturn(false)
        val result = processMemberVerificationResponseHandler.invoke(state, Record(TOPIC, member.toString(), RegistrationCommand(command)))

        verify(membershipPersistenceClient, never()).setRegistrationRequestStatus(
            any(),
            any(),
            any(),
        )

        assertThat(result.outputStates).hasSize(1)
            .anyMatch {
                ((it.value as? RegistrationCommand)?.command as? DeclineRegistration)?.reason?.isNotBlank() == true
            }
    }

    @Test
    fun `handler use the correct TTL configuration`() {
        whenever(membershipQueryClient.getApprovalRules(any(), any())).doReturn(MembershipQueryResult.Success(emptyList()))
        processMemberVerificationResponseHandler.invoke(
            state,
            Record(TOPIC, member.toString(), RegistrationCommand(command))
        )

        verify(config).getIsNull("$TTLS.$UPDATE_TO_PENDING_AUTO_APPROVAL")
    }

    @Test
    fun `exception is thrown when RegistrationState is null`() {
        assertThrows<MissingRegistrationStateException> {
            processMemberVerificationResponseHandler.invoke(null, Record(TOPIC, member.toString(), RegistrationCommand(command)))
        }
    }
}