package net.corda.membership.impl.registration.dynamic.handler.mgm

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.membership.SignedData
import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.data.membership.command.registration.mgm.ApproveRegistration
import net.corda.data.membership.command.registration.mgm.DeclineRegistration
import net.corda.data.membership.command.registration.mgm.ProcessMemberVerificationResponse
import net.corda.data.membership.common.ApprovalRuleDetails
import net.corda.data.membership.common.ApprovalRuleType
import net.corda.data.membership.common.RegistrationRequestDetails
import net.corda.data.membership.common.v2.RegistrationStatus
import net.corda.data.membership.p2p.VerificationResponse
import net.corda.data.membership.p2p.v2.SetOwnRegistrationStatus
import net.corda.data.membership.preauth.PreAuthToken
import net.corda.data.membership.state.CompletedCommandMetadata
import net.corda.data.membership.state.RegistrationState
import net.corda.data.p2p.app.AppMessage
import net.corda.data.p2p.app.MembershipStatusFilter
import net.corda.libs.configuration.SmartConfig
import net.corda.membership.impl.registration.VerificationResponseKeys
import net.corda.membership.impl.registration.dynamic.handler.MemberTypeChecker
import net.corda.membership.impl.registration.dynamic.handler.RegistrationHandlerResult
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_PENDING
import net.corda.membership.lib.MemberInfoExtension.Companion.STATUS
import net.corda.membership.lib.VersionedMessageBuilder
import net.corda.membership.lib.registration.PRE_AUTH_TOKEN
import net.corda.membership.p2p.helpers.MembershipP2pRecordsFactory
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipPersistenceOperation
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
import org.apache.avro.specific.SpecificRecordBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.mockito.Mockito
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
import java.nio.ByteBuffer
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
    private val expectedRegistrationTopicKey = "${member.x500Name}-${member.groupId}"
    private val state = RegistrationState(
        REGISTRATION_ID,
        member,
        mgm,
        emptyList()
    )
    private val setRegistrationRequestStatusCommands = listOf(
        Record(
            "topic",
            "key",
            "value",
        ),
    )
    private val operation = mock<MembershipPersistenceOperation<Unit>> {
        on { createAsyncCommands() } doReturn setRegistrationRequestStatusCommands
    }

    private val membershipPersistenceClient = mock<MembershipPersistenceClient> {
        on {
            setRegistrationRequestStatus(
                any(),
                any(),
                any(),
                anyOrNull()
            )
        } doReturn operation
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
    private val memberContextBytes = byteArrayOf(1)
    private val registrationContextKeyValues = emptyList<KeyValuePair>()
    private val registrationContext = mock<KeyValuePairList> {
        on { items } doReturn registrationContextKeyValues
    }
    private val registrationContextBytes = byteArrayOf(2)
    private val requestStatus = mock<RegistrationRequestDetails> {
        on { memberProvidedContext } doReturn SignedData(ByteBuffer.wrap(memberContextBytes), mock(), mock())
        on { registrationContext } doReturn SignedData(ByteBuffer.wrap(registrationContextBytes), mock(), mock())
    }
    private val membershipQueryClient = mock<MembershipQueryClient> {
        on {
            queryRegistrationRequest(eq(mgm.toCorda()), any())
        } doReturn MembershipQueryResult.Success(requestStatus)
    }
    private val groupReader = mock<MembershipGroupReader>()
    private val membershipGroupReaderProvider = mock<MembershipGroupReaderProvider> {
        on { getGroupReader(any()) } doReturn groupReader
    }
    private val record = mock<Record<String, AppMessage>>()
    private val capturedStatus = argumentCaptor<SpecificRecordBase>()
    private val membershipP2PRecordsFactory = mock<MembershipP2pRecordsFactory> {
        on {
            createAuthenticatedMessageRecord(
                eq(mgm),
                eq(member),
                capturedStatus.capture(),
                any(),
                any(),
                eq(MembershipStatusFilter.PENDING),
            )
        } doReturn record
    }
    private val memberTypeChecker = mock<MemberTypeChecker> {
        on { isMgm(mgm) } doReturn true
        on { isMgm(member) } doReturn false
    }
    private val config = mock<SmartConfig>()
    private val deserializer = mock<CordaAvroDeserializer<KeyValuePairList>> {
        on { deserialize(eq(memberContextBytes)) } doReturn memberContext
        on { deserialize(eq(registrationContextBytes)) } doReturn registrationContext
    }
    private val cordaAvroSerializationFactory = mock<CordaAvroSerializationFactory> {
        on { createAvroDeserializer(any(), eq(KeyValuePairList::class.java)) } doReturn deserializer
    }

    private val processMemberVerificationResponseHandler = ProcessMemberVerificationResponseHandler(
        membershipPersistenceClient,
        mock(),
        cordaAvroSerializationFactory,
        memberTypeChecker,
        config,
        membershipQueryClient,
        membershipGroupReaderProvider,
        membershipP2PRecordsFactory,
    )

    @Test
    fun `handler returns approve member command with auto-approval status`() {
        mockApprovalRules(ApprovalRuleType.STANDARD)
        mockMemberLookup(memberContextKeyValues, MEMBER_STATUS_PENDING)

        val result = invokeTestFunction()

        verifySetRegistrationStatus(RegistrationStatus.PENDING_AUTO_APPROVAL)
        verifySetOwnRegistrationStatus(RegistrationStatus.PENDING_AUTO_APPROVAL)
        verifyGetApprovalRules(ApprovalRuleType.STANDARD)

        assertThat(result.outputStates).hasSize(3)
            .contains(record)
            .containsAll(setRegistrationRequestStatusCommands)
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
        mockMemberLookup(memberContextKeyValues, MEMBER_STATUS_PENDING)
        val result = invokeTestFunction()

        verifySetRegistrationStatus(RegistrationStatus.PENDING_MANUAL_APPROVAL)
        verifySetOwnRegistrationStatus(RegistrationStatus.PENDING_MANUAL_APPROVAL)
        verifyGetApprovalRules(ApprovalRuleType.STANDARD)

        assertThat(result.outputStates).hasSize(2)
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
        mockMemberLookup(memberContextKeyValues, MEMBER_STATUS_PENDING)
        invokeTestFunction()

        verify(config).getIsNull("$TTLS.$UPDATE_TO_PENDING_AUTO_APPROVAL")
    }

    @Test
    fun `processing is skipped when RegistrationState is null`() {
        val result = assertDoesNotThrow {
            invokeTestFunction(null)
        }
        assertThat(result.updatedState).isNull()
        assertThat(result.outputStates).isEmpty()
    }

    @Test
    fun `processing is skipped when command has been processed previously`() {
        val inputState = RegistrationState(
            state.registrationId,
            state.registeringMember,
            state.mgm,
            listOf(CompletedCommandMetadata(1, processMemberVerificationResponseHandler.commandType.simpleName))
        )
        val result = assertDoesNotThrow {
            invokeTestFunction(inputState)
        }
        assertThat(result.updatedState).isEqualTo(inputState)
        assertThat(result.outputStates).isEmpty()
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
            val operation = object : MembershipPersistenceOperation<Unit> {
                override fun execute(): MembershipPersistenceResult<Unit> = result

                override fun createAsyncCommands(): Collection<Record<*, *>> {
                    return emptyList()
                }
            }
            whenever(
                membershipPersistenceClient.consumePreAuthToken(
                    mgm.toCorda(),
                    member.toCorda().x500Name,
                    preAuthToken
                )
            ).doReturn(operation)
        }

        private fun mockMemberContext(
            additionalContextItem: KeyValuePair
        ) {
            whenever(memberContext.items).doReturn(registrationContextKeyValues + additionalContextItem)
        }

        private fun mockPreAuthTokenInRegistrationContext(
            token: String = preAuthToken.toString()
        ) {
            val context = registrationContextKeyValues +
                KeyValuePair(PRE_AUTH_TOKEN, token)
            whenever(registrationContext.items).doReturn(context)
        }

        @Suppress("MaxLineLength")
        @Test
        fun `handler sets initial registration request with valid pre-auth token to status manual approval if there are pre-auth token rules`() {
            mockConsumeToken()
            mockQueryToken(MembershipQueryResult.Success(listOf(mockToken)))
            mockPreAuthTokenInRegistrationContext()
            mockApprovalRules(ApprovalRuleType.PREAUTH, manuallyApproveAllRule)
            mockMemberLookup(memberContextKeyValues, MEMBER_STATUS_PENDING)

            val result = invokeTestFunction()

            assertThat(result.outputStates).hasSize(2)
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
            mockMemberLookup(memberContextKeyValues, MEMBER_STATUS_PENDING)

            mockConsumeToken()
            mockQueryToken(MembershipQueryResult.Success(listOf(mockToken)))
            mockPreAuthTokenInRegistrationContext()
            mockApprovalRules(ApprovalRuleType.PREAUTH, manuallyApproveTestKeyRule)

            val result = invokeTestFunction()

            assertThat(result.outputStates).hasSize(2)
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
            mockMemberLookup(memberContextKeyValues, MEMBER_STATUS_PENDING)

            mockConsumeToken()
            mockQueryToken(MembershipQueryResult.Success(listOf(mockToken)))
            mockPreAuthTokenInRegistrationContext()
            mockMemberContext(KeyValuePair(ADDITIONAL_TEST_KEY, ADDITIONAL_TEST_VALUE))
            mockApprovalRules(ApprovalRuleType.PREAUTH, manuallyApproveTestKeyRule)

            val result = invokeTestFunction()

            assertThat(result.outputStates).hasSize(2)
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
            mockMemberLookup(memberContextKeyValues, MEMBER_STATUS_PENDING)

            mockConsumeToken()
            mockQueryToken(MembershipQueryResult.Success(listOf(mockToken)))
            mockPreAuthTokenInRegistrationContext()
            mockMemberContext(KeyValuePair(ADDITIONAL_TEST_KEY, "$ADDITIONAL_TEST_VALUE.changed"))
            mockApprovalRules(ApprovalRuleType.PREAUTH, manuallyApproveTestKeyRule)

            val result = invokeTestFunction()

            assertThat(result.outputStates).hasSize(2)
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
            mockMemberLookup(memberContextKeyValues, MEMBER_STATUS_PENDING)

            val result = invokeTestFunction()

            assertThat(result.outputStates).hasSize(3)
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

            assertThat(result.outputStates).hasSize(2)
            assertUpdatedState(result)

            verifyGetApprovalRules(ApprovalRuleType.PREAUTH)
            verifySetRegistrationStatus(RegistrationStatus.PENDING_MANUAL_APPROVAL)
            verifySetOwnRegistrationStatus(RegistrationStatus.PENDING_MANUAL_APPROVAL)
        }

        @Test
        fun `handler does not send registration status update message when status cannot be retrieved`() {
            mockConsumeToken()
            mockQueryToken(MembershipQueryResult.Success(listOf(mockToken)))
            mockPreAuthTokenInRegistrationContext()
            mockApprovalRules(ApprovalRuleType.PREAUTH, manuallyApproveNoneRule)
            mockMemberLookup(memberContextKeyValues, MEMBER_STATUS_PENDING)

            val mockedBuilder = Mockito.mockStatic(VersionedMessageBuilder::class.java).also {
                it.`when`<VersionedMessageBuilder> {
                    VersionedMessageBuilder.retrieveRegistrationStatusMessage(any(), any(), any(), any())
                } doReturn null
            }

            val results = invokeTestFunction()
            verify(membershipP2PRecordsFactory, never()).createAuthenticatedMessageRecord(any(), any(), any(), anyOrNull(), any(), any())
            assertThat(results.outputStates)
                .hasSize(2)
            results.outputStates.forEach { assertThat(it.value).isNotInstanceOf(AppMessage::class.java) }

            mockedBuilder.close()
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
        regState,
        Record(TOPIC, "${member.x500Name}-${member.groupId}", RegistrationCommand(regCommand))
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
        verify(membershipP2PRecordsFactory).createAuthenticatedMessageRecord(
            eq(mgm),
            eq(member),
            argThat<SetOwnRegistrationStatus> {
                registrationId == REGISTRATION_ID && newStatus == status
            },
            any(),
            any(),
            eq(MembershipStatusFilter.PENDING),
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
            on { platformVersion } doReturn 50100
        }
        if (memberStatus == MEMBER_STATUS_ACTIVE) {
            whenever(groupReader.lookup(this.member.toCorda().x500Name)).doReturn(member)
        } else {
            whenever(groupReader.lookup(this.member.toCorda().x500Name, MembershipStatusFilter.PENDING)).doReturn(member)
        }
    }
}
