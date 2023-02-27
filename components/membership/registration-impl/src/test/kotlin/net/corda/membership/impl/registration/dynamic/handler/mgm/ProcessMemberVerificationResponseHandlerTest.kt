package net.corda.membership.impl.registration.dynamic.handler.mgm

import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.data.membership.command.registration.mgm.ApproveRegistration
import net.corda.data.membership.command.registration.mgm.DeclineRegistration
import net.corda.data.membership.command.registration.mgm.ProcessMemberVerificationResponse
import net.corda.data.membership.common.ApprovalRuleDetails
import net.corda.data.membership.common.ApprovalRuleType
import net.corda.data.membership.common.RegistrationStatus
import net.corda.data.membership.p2p.SetOwnRegistrationStatus
import net.corda.data.membership.p2p.VerificationResponse
import net.corda.data.membership.preauth.PreAuthToken
import net.corda.data.membership.state.RegistrationState
import net.corda.data.p2p.app.AppMessage
import net.corda.libs.configuration.SmartConfig
import net.corda.membership.impl.registration.VerificationResponseKeys
import net.corda.membership.impl.registration.dynamic.handler.MemberTypeChecker
import net.corda.membership.impl.registration.dynamic.handler.MissingRegistrationStateException
import net.corda.membership.impl.registration.dynamic.handler.RegistrationHandlerResult
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_PENDING
import net.corda.membership.lib.MemberInfoExtension.Companion.PRE_AUTH_TOKEN
import net.corda.membership.lib.MemberInfoExtension.Companion.STATUS
import net.corda.membership.lib.registration.RegistrationRequestStatus
import net.corda.membership.p2p.helpers.P2pRecordsFactory
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipPersistenceResult
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.persistence.client.MembershipQueryResult
import net.corda.membership.read.MembershipGroupReader
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.records.Record
import net.corda.schema.configuration.MembershipConfig.TtlsConfig.TTLS
import net.corda.schema.configuration.MembershipConfig.TtlsConfig.UPDATE_TO_PENDING_AUTO_APPROVAL
import net.corda.test.util.identity.createTestHoldingIdentity
import net.corda.v5.membership.MGMContext
import net.corda.v5.membership.MemberContext
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.toAvro
import net.corda.virtualnode.toCorda
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

class ProcessMemberVerificationResponseHandlerTest {
    private companion object {
        const val GROUP_ID = "ABC123"
        const val REGISTRATION_ID = "REG-01"
        const val TOPIC = "dummyTopic"
        const val APPROVE_ALL_STRING = "^*"
        const val APPROVE_NONE_STRING = "^ThisShouldNotMatchAnyKey$"
        const val MEMBER_KEY = "member"
        const val ADDITIONAL_TEST_KEY = "corda.additional.test.key"
        const val ADDITIONAL_TEST_VALUE = "corda.additional.test.value"
    }

    val mockToken: PreAuthToken = mock()

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
    private val expectedRegistrationTopicKey = "$REGISTRATION_ID-${mgm.toCorda().shortHash}"
    private val state = RegistrationState(
        REGISTRATION_ID,
        member,
        mgm
    )

    private val membershipPersistenceClient = mock<MembershipPersistenceClient> {
        on {
            setRegistrationRequestStatus(
                eq(mgm.toCorda()),
                eq(REGISTRATION_ID),
                eq(RegistrationStatus.PENDING_AUTO_APPROVAL),
                anyOrNull()
            )
        } doReturn MembershipPersistenceResult.success()
    }
    private val manuallyApproveAllRule = mock<ApprovalRuleDetails> {
        on { ruleRegex } doReturn APPROVE_ALL_STRING
    }
    private val manuallyApproveNoneRule = mock<ApprovalRuleDetails> {
        on { ruleRegex } doReturn APPROVE_NONE_STRING
    }
    private val manuallyApproveTestKeyRule = mock<ApprovalRuleDetails> {
        on { ruleRegex } doReturn "^$ADDITIONAL_TEST_KEY$"
    }

    private val memberContextKeyValues = listOf(KeyValuePair(MEMBER_KEY, MEMBER_KEY))
    private val memberContext = mock<KeyValuePairList> {
        on { items } doReturn memberContextKeyValues
    }
    private val requestStatus = mock<RegistrationRequestStatus> {
        on { memberContext } doReturn memberContext
    }
    private val membershipQueryClient = mock<MembershipQueryClient> {
        on {
            queryRegistrationRequestStatus(eq(mgm.toCorda()), any())
        } doReturn MembershipQueryResult.Success(requestStatus)
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
        mockApprovalRules(ApprovalRuleType.STANDARD)

        val result = invokeTestFunction()

        verifySetRegistrationStatus(RegistrationStatus.PENDING_AUTO_APPROVAL)
        verifySetOwnRegistrationStatus(RegistrationStatus.PENDING_AUTO_APPROVAL)
        verifyGetApprovalRules(ApprovalRuleType.STANDARD)

        assertThat(result.outputStates).hasSize(2)
            .contains(record)
            .anyMatch {
                val value = it.value
                it.key == expectedRegistrationTopicKey &&
                        value is RegistrationCommand &&
                        value.command is ApproveRegistration
            }

        assertUpdatedState(result)
    }

    @Test
    fun `handler sets request status to manual approval`() {
        mockApprovalRules(ApprovalRuleType.STANDARD, manuallyApproveAllRule)
        val result = invokeTestFunction()

        verifySetRegistrationStatus(RegistrationStatus.PENDING_MANUAL_APPROVAL)
        verifySetOwnRegistrationStatus(RegistrationStatus.PENDING_MANUAL_APPROVAL)
        verifyGetApprovalRules(ApprovalRuleType.STANDARD)

        assertThat(result.outputStates).hasSize(1)
        assertUpdatedState(result)
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

        val result = invokeTestFunction(regCommand = command)

        verifyNeverSetRegistrationStatus()
        assertDeclinedRegistrationOutput(result)
    }

    @Test
    fun `handler returns decline member command if the member is an MGM`() {
        whenever(memberTypeChecker.isMgm(member)).doReturn(true)

        val result = invokeTestFunction()

        verifyNeverSetRegistrationStatus()
        assertDeclinedRegistrationOutput(result)
    }

    @Test
    fun `handler returns decline member command if the mgm is not an MGM`() {
        whenever(memberTypeChecker.isMgm(mgm)).doReturn(false)

        val result = invokeTestFunction()

        verifyNeverSetRegistrationStatus()
        assertDeclinedRegistrationOutput(result)
    }

    @Test
    fun `handler use the correct TTL configuration`() {
        mockApprovalRules(ApprovalRuleType.STANDARD)
        invokeTestFunction()

        verify(config).getIsNull("$TTLS.$UPDATE_TO_PENDING_AUTO_APPROVAL")
    }

    @Test
    fun `exception is thrown when RegistrationState is null`() {
        assertThrows<MissingRegistrationStateException> {
            invokeTestFunction(null)
        }
    }

    @Nested
    inner class PreAuthTokenTest {

        private val preAuthToken = UUID(0, 1)

        private fun mockQueryToken(
            result: MembershipQueryResult<List<PreAuthToken>>
        ) {
            whenever(
                membershipQueryClient.queryPreAuthTokens(
                    mgm.toCorda(),
                    member.toCorda().x500Name,
                    preAuthToken,
                    false
                )
            )
                .doReturn(result)
        }

        private fun mockConsumeToken(
            result: MembershipPersistenceResult<Unit> = MembershipPersistenceResult.success()
        ) {
            whenever(
                membershipPersistenceClient.consumePreAuthToken(
                    mgm.toCorda(),
                    member.toCorda().x500Name,
                    preAuthToken
                )
            ).doReturn(result)
        }

        private fun mockPreAuthTokenInRegistrationContext(
            token: String = preAuthToken.toString(),
            additionalContextItem: KeyValuePair? = null
        ) {
            val context = memberContextKeyValues +
                    KeyValuePair(PRE_AUTH_TOKEN, token) +
                    additionalContextItem
            whenever(memberContext.items).doReturn(context.filterNotNull())
        }

        @Suppress("MaxLineLength")
        @Test
        fun `handler sets initial registration request with valid pre-auth token to status manual approval if there are pre-auth token rules`() {
            mockConsumeToken()
            mockQueryToken(MembershipQueryResult.Success(listOf(mockToken)))
            mockPreAuthTokenInRegistrationContext()
            mockApprovalRules(ApprovalRuleType.PREAUTH, manuallyApproveAllRule)

            val result = invokeTestFunction()

            assertThat(result.outputStates).hasSize(1)
            assertUpdatedState(result)

            verifyGetApprovalRules(ApprovalRuleType.PREAUTH)
            verifySetRegistrationStatus(RegistrationStatus.PENDING_MANUAL_APPROVAL)
            verifySetOwnRegistrationStatus(RegistrationStatus.PENDING_MANUAL_APPROVAL)
        }

        @Suppress("MaxLineLength")
        @Test
        fun `handler sets re-registration request with valid pre-auth token to status manual approval if there are pre-auth token rules checking for removed key`() {
            // Configure active member for re-registration scenario
            mockMemberLookup(
                memberContextKeyValues + KeyValuePair(ADDITIONAL_TEST_KEY, ADDITIONAL_TEST_VALUE)
            )

            mockConsumeToken()
            mockQueryToken(MembershipQueryResult.Success(listOf(mockToken)))
            mockPreAuthTokenInRegistrationContext()
            mockApprovalRules(ApprovalRuleType.PREAUTH, manuallyApproveTestKeyRule)

            val result = invokeTestFunction()

            assertThat(result.outputStates).hasSize(1)
            assertUpdatedState(result)

            verifyGetApprovalRules(ApprovalRuleType.PREAUTH)
            verifySetRegistrationStatus(RegistrationStatus.PENDING_MANUAL_APPROVAL)
            verifySetOwnRegistrationStatus(RegistrationStatus.PENDING_MANUAL_APPROVAL)
        }

        @Suppress("MaxLineLength")
        @Test
        fun `handler sets re-registration request with valid pre-auth token to status manual approval if there are pre-auth token rules checking for added key`() {
            // Configure active member for re-registration scenario
            mockMemberLookup(memberContextKeyValues)

            mockConsumeToken()
            mockQueryToken(MembershipQueryResult.Success(listOf(mockToken)))
            mockPreAuthTokenInRegistrationContext(
                additionalContextItem = KeyValuePair(ADDITIONAL_TEST_KEY, ADDITIONAL_TEST_VALUE)
            )
            mockApprovalRules(ApprovalRuleType.PREAUTH, manuallyApproveTestKeyRule)

            val result = invokeTestFunction()

            assertThat(result.outputStates).hasSize(1)
            assertUpdatedState(result)

            verifyGetApprovalRules(ApprovalRuleType.PREAUTH)
            verifySetRegistrationStatus(RegistrationStatus.PENDING_MANUAL_APPROVAL)
            verifySetOwnRegistrationStatus(RegistrationStatus.PENDING_MANUAL_APPROVAL)
        }

        @Suppress("MaxLineLength")
        @Test
        fun `handler sets re-registration request with valid pre-auth token to status manual approval if there are pre-auth token rules checking for changed key`() {
            // Configure active member for re-registration scenario
            mockMemberLookup(
                memberContextKeyValues + KeyValuePair(ADDITIONAL_TEST_KEY, ADDITIONAL_TEST_VALUE)
            )

            mockConsumeToken()
            mockQueryToken(MembershipQueryResult.Success(listOf(mockToken)))
            mockPreAuthTokenInRegistrationContext(
                additionalContextItem = KeyValuePair(ADDITIONAL_TEST_KEY, "$ADDITIONAL_TEST_VALUE.changed")
            )
            mockApprovalRules(ApprovalRuleType.PREAUTH, manuallyApproveTestKeyRule)

            val result = invokeTestFunction()

            assertThat(result.outputStates).hasSize(1)
            assertUpdatedState(result)

            verifyGetApprovalRules(ApprovalRuleType.PREAUTH)
            verifySetRegistrationStatus(RegistrationStatus.PENDING_MANUAL_APPROVAL)
            verifySetOwnRegistrationStatus(RegistrationStatus.PENDING_MANUAL_APPROVAL)
        }

        @Test
        fun `handler starts auto approval if there are pre auth approval rules but none match`() {
            mockConsumeToken()
            mockQueryToken(MembershipQueryResult.Success(listOf(mockToken)))
            mockPreAuthTokenInRegistrationContext()
            mockApprovalRules(ApprovalRuleType.PREAUTH, manuallyApproveNoneRule)

            val result = invokeTestFunction()

            assertThat(result.outputStates).hasSize(2)
            assertUpdatedState(result)

            verifyGetApprovalRules(ApprovalRuleType.PREAUTH)
            verifySetRegistrationStatus(RegistrationStatus.PENDING_AUTO_APPROVAL)
            verifySetOwnRegistrationStatus(RegistrationStatus.PENDING_AUTO_APPROVAL)
        }

        @Test
        fun `handler starts doesn't include pending member info in comparison against the registration request context`() {
            mockMemberLookup(
                memberContextKeyValues,
                MEMBER_STATUS_PENDING
            )

            mockConsumeToken()
            mockQueryToken(MembershipQueryResult.Success(listOf(mockToken)))
            mockPreAuthTokenInRegistrationContext()
            mockApprovalRules(
                ApprovalRuleType.PREAUTH,
                ApprovalRuleDetails(
                    UUID(0, 1).toString(),
                    "^$MEMBER_KEY$",
                    "match member key"
                )
            )

            val result = invokeTestFunction()

            assertThat(result.outputStates).hasSize(1)
            assertUpdatedState(result)

            verifyGetApprovalRules(ApprovalRuleType.PREAUTH)
            verifySetRegistrationStatus(RegistrationStatus.PENDING_MANUAL_APPROVAL)
            verifySetOwnRegistrationStatus(RegistrationStatus.PENDING_MANUAL_APPROVAL)
        }

        @Test
        fun `handler declines registration if invalid pre auth token is provided`() {
            mockPreAuthTokenInRegistrationContext("bad-token")

            val result = invokeTestFunction()

            assertUpdatedState(result)

            verifyNeverSetRegistrationStatus()
            assertDeclinedRegistrationOutput(result)
        }

        @Test
        fun `handler declines registration if no token exists`() {
            mockQueryToken(MembershipQueryResult.Success(emptyList()))
            mockPreAuthTokenInRegistrationContext()

            val result = invokeTestFunction()

            assertUpdatedState(result)

            verifyNeverSetRegistrationStatus()
            assertDeclinedRegistrationOutput(result)
        }

        @Test
        fun `handler declines registration if failure to check for existing tokens`() {
            mockQueryToken(MembershipQueryResult.Failure("failed"))
            mockPreAuthTokenInRegistrationContext()

            val result = invokeTestFunction()

            assertUpdatedState(result)

            verifyNeverSetRegistrationStatus()
            assertDeclinedRegistrationOutput(result)
        }

        @Test
        fun `handler declines registration if impossible to consume token`() {
            mockConsumeToken(MembershipPersistenceResult.Failure("error"))
            mockQueryToken(MembershipQueryResult.Success(listOf(mockToken)))
            mockPreAuthTokenInRegistrationContext()
            mockApprovalRules(ApprovalRuleType.PREAUTH, manuallyApproveAllRule)

            val result = invokeTestFunction()

            assertUpdatedState(result)

            verifyNeverSetRegistrationStatus()
            assertDeclinedRegistrationOutput(result)
        }
    }

    private fun invokeTestFunction(
        regState: RegistrationState? = state,
        regCommand: Any = command
    ) = processMemberVerificationResponseHandler.invoke(
        regState, Record(TOPIC, member.toString(), RegistrationCommand(regCommand))
    )

    private fun mockApprovalRules(
        type: ApprovalRuleType,
        vararg rules: ApprovalRuleDetails
    ) {
        whenever(membershipQueryClient.getApprovalRules(any(), eq(type)))
            .doReturn(MembershipQueryResult.Success(rules.toList()))
    }

    private fun verifyNeverSetRegistrationStatus() {
        verify(membershipPersistenceClient, never()).setRegistrationRequestStatus(
            any(),
            any(),
            any(),
            anyOrNull()
        )
    }

    private fun verifySetRegistrationStatus(status: RegistrationStatus) {
        verify(membershipPersistenceClient).setRegistrationRequestStatus(
            eq(mgm.toCorda()),
            eq(REGISTRATION_ID),
            eq(status),
            anyOrNull()
        )
    }

    private fun verifySetOwnRegistrationStatus(status: RegistrationStatus) {
        verify(p2pRecordsFactory).createAuthenticatedMessageRecord(
            eq(mgm),
            eq(member),
            argThat<SetOwnRegistrationStatus> {
                registrationId == REGISTRATION_ID && newStatus == status
            },
            any(),
            any()
        )
    }

    private fun verifyGetApprovalRules(type: ApprovalRuleType) {
        verify(membershipQueryClient).getApprovalRules(mgm.toCorda(), type)
    }

    private fun assertUpdatedState(result: RegistrationHandlerResult) {
        assertThat(result.updatedState).isNotNull
        val state = result.updatedState!!
        assertThat(state.registeringMember).isEqualTo(member)
        assertThat(state.mgm).isEqualTo(mgm)
        assertThat(state.registrationId).isEqualTo(REGISTRATION_ID)
    }

    private fun assertDeclinedRegistrationOutput(result: RegistrationHandlerResult) {
        assertThat(result.outputStates).hasSize(1)
            .anyMatch {
                ((it.value as? RegistrationCommand)?.command as? DeclineRegistration)?.reason?.isNotBlank() == true
            }
    }

    private fun mockMemberLookup(
        memberProperties: List<KeyValuePair>,
        memberStatus: String = MEMBER_STATUS_ACTIVE
    ) {
        val memberContext = mock<MemberContext> {
            on { entries } doReturn memberProperties.associate { it.key to it.value }.entries
        }
        val mgmContext = mock<MGMContext> {
            on { parse(STATUS, String::class.java) } doReturn memberStatus
        }
        val member = mock<MemberInfo> {
            on { memberProvidedContext } doReturn memberContext
            on { mgmProvidedContext } doReturn mgmContext
        }
        whenever(groupReader.lookup(this.member.toCorda().x500Name)).doReturn(member)
    }
}
