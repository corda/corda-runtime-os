package net.corda.membership.impl.registration.dynamic.handler.mgm

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.membership.actions.request.DistributeMemberInfo
import net.corda.data.membership.actions.request.MembershipActionsRequest
import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.data.membership.command.registration.mgm.ApproveRegistration
import net.corda.data.membership.command.registration.mgm.CheckForPendingRegistration
import net.corda.data.membership.command.registration.mgm.DeclineRegistration
import net.corda.data.membership.common.v2.RegistrationStatus
import net.corda.data.membership.p2p.v2.SetOwnRegistrationStatus
import net.corda.data.membership.state.RegistrationState
import net.corda.data.p2p.app.AppMessage
import net.corda.data.p2p.app.MembershipStatusFilter
import net.corda.membership.groupparams.writer.service.GroupParametersWriterService
import net.corda.membership.impl.registration.dynamic.handler.MemberTypeChecker
import net.corda.membership.impl.registration.dynamic.handler.MissingRegistrationStateException
import net.corda.membership.impl.registration.dynamic.handler.TestUtils.createHoldingIdentity
import net.corda.membership.impl.registration.dynamic.handler.TestUtils.mockMemberInfo
import net.corda.membership.lib.MemberInfoExtension.Companion.holdingIdentity
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.lib.SignedGroupParameters
import net.corda.membership.lib.VersionedMessageBuilder
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipPersistenceOperation
import net.corda.membership.persistence.client.MembershipPersistenceResult
import net.corda.membership.read.MembershipGroupReader
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.records.Record
import net.corda.p2p.messaging.P2pRecordsFactory
import net.corda.p2p.messaging.P2pRecordsFactory.Companion.MEMBERSHIP_REGISTRATION_PREFIX
import net.corda.schema.Schemas.Membership.MEMBERSHIP_ACTIONS_TOPIC
import net.corda.schema.Schemas.Membership.MEMBER_LIST_TOPIC
import net.corda.schema.Schemas.Membership.REGISTRATION_COMMAND_TOPIC
import net.corda.test.util.time.TestClock
import net.corda.v5.membership.NotaryInfo
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toAvro
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

class ApproveRegistrationHandlerTest {
    private val owner = createHoldingIdentity("owner")
    private val member = createHoldingIdentity("member")
    private val notary = createHoldingIdentity("notary")
    private val registrationId = "registrationID"
    private val command = ApproveRegistration()
    private val state = RegistrationState(registrationId, member.toAvro(), owner.toAvro(), emptyList())
    private val key = "key"
    private val mockSignedGroupParameters = mock<SignedGroupParameters> {
        on { epoch } doReturn 6
    }
    private val memberInfo = mockMemberInfo(member)
    private val notaryInfo = mockMemberInfo(notary, isNotary = true)
    private val mgm = mockMemberInfo(
        createHoldingIdentity("mgm"),
        isMgm = true,
    )
    private class SuccessOperation<T>(
        private val result: T,
    ) : MembershipPersistenceOperation<T> {
        override fun execute() = MembershipPersistenceResult.Success(result)

        override fun createAsyncCommands() = emptyList<Record<*, *>>()
    }
    private val persistentMemberInfo: PersistentMemberInfo = mock()
    private val persistentNotaryInfo: PersistentMemberInfo = mock()
    private val membershipPersistenceClient = mock<MembershipPersistenceClient> {
        on {
            setMemberAndRegistrationRequestAsApproved(
                owner,
                member,
                registrationId
            )
        } doReturn SuccessOperation(persistentMemberInfo)
        on {
            setMemberAndRegistrationRequestAsApproved(
                owner,
                notary,
                registrationId
            )
        } doReturn SuccessOperation(persistentNotaryInfo)
        on {
            addNotaryToGroupParameters(
                persistentNotaryInfo
            )
        } doReturn SuccessOperation(mockSignedGroupParameters)
    }
    private val clock = TestClock(Instant.ofEpochMilli(0))
    private val cordaAvroSerializationFactory = mock<CordaAvroSerializationFactory>()
    private val record = mock<Record<String, AppMessage>>()
    private val membershipP2PRecordsFactory = mock<P2pRecordsFactory> {
        on {
            createMembershipAuthenticatedMessageRecord(
                any(),
                any(),
                any(),
                eq(MEMBERSHIP_REGISTRATION_PREFIX),
                anyOrNull(),
                eq(MembershipStatusFilter.ACTIVE_OR_SUSPENDED),
            )
        } doReturn record
    }
    private val memberTypeChecker = mock<MemberTypeChecker> {
        on { isMgm(member.toAvro()) } doReturn false
        on { getMgmMemberInfo(owner) } doReturn mgm
    }
    private val mockGroupParameters: SignedGroupParameters = mock {
        on { epoch } doReturn 5
    }
    private val groupReader: MembershipGroupReader = mock {
        on { groupParameters } doReturn mockGroupParameters
    }
    private val groupReaderProvider: MembershipGroupReaderProvider = mock {
        on { getGroupReader(any()) } doReturn groupReader
    }
    private val writerService: GroupParametersWriterService = mock()

    private val memberInfoFactory: MemberInfoFactory = mock {
        on { createMemberInfo(eq(persistentMemberInfo)) } doReturn memberInfo
        on { createMemberInfo(eq(persistentNotaryInfo)) } doReturn notaryInfo
    }

    private val handler = ApproveRegistrationHandler(
        membershipPersistenceClient,
        clock,
        cordaAvroSerializationFactory,
        memberTypeChecker,
        groupReaderProvider,
        writerService,
        memberInfoFactory,
        membershipP2PRecordsFactory,
    )

    @Test
    fun `invoke return member record`() {
        val reply = handler.invoke(state, key, command)

        val memberRecords = reply.outputStates.filter {
            it.topic == MEMBER_LIST_TOPIC
        }
        assertThat(memberRecords)
            .hasSize(1)
            .allSatisfy {
                assertThat(it.key).isEqualTo("${owner.shortHash}-${member.shortHash}")
                val value = it.value as? PersistentMemberInfo
                assertThat(value).isEqualTo(persistentMemberInfo)
            }
    }

    @Test
    fun `invoke sends the approved state to the member over P2P`() {
        val record = mock<Record<String, AppMessage>>()
        whenever(
            membershipP2PRecordsFactory.createMembershipAuthenticatedMessageRecord(
                eq(owner.toAvro()),
                eq(member.toAvro()),
                eq(
                    SetOwnRegistrationStatus(
                        registrationId,
                        RegistrationStatus.APPROVED,
                        null
                    )
                ),
                eq(MEMBERSHIP_REGISTRATION_PREFIX),
                anyOrNull(),
                eq(MembershipStatusFilter.ACTIVE_OR_SUSPENDED),
            )
        ).doReturn(record)

        val reply = handler.invoke(state, key, command)

        assertThat(reply.outputStates).contains(record)
    }

    @Test
    fun `invoke update the member and request state`() {
        handler.invoke(state, key, command)

        verify(membershipPersistenceClient).setMemberAndRegistrationRequestAsApproved(
            viewOwningIdentity = owner,
            approvedMember = member,
            registrationRequestId = registrationId,
        )
    }

    @Test
    fun `invoke updates the MGM's view of group parameters with notary, if approved member has notary role set`() {
        val state = RegistrationState(registrationId, notary.toAvro(), owner.toAvro(), emptyList())

        val results = handler.invoke(state, key, command)

        verify(membershipPersistenceClient).addNotaryToGroupParameters(persistentNotaryInfo)
        assertThat(results.outputStates)
            .hasSize(4)

        val actionsRequest = results.outputStates.single { it.topic == MEMBERSHIP_ACTIONS_TOPIC }
        val distributeMemberInfo = (actionsRequest.value as? MembershipActionsRequest)?.request as? DistributeMemberInfo
        assertThat(distributeMemberInfo?.minimumGroupParametersEpoch).isEqualTo(6)

        val registrationCommand = results.outputStates.single { it.topic == REGISTRATION_COMMAND_TOPIC }
        val checkForPendingRegistration = (registrationCommand.value as? RegistrationCommand)?.command as? CheckForPendingRegistration
        assertThat(checkForPendingRegistration?.mgm).isEqualTo(owner.toAvro())
        assertThat(checkForPendingRegistration?.member).isEqualTo(notary.toAvro())
        assertThat(checkForPendingRegistration?.numberOfRetriesSoFar).isEqualTo(0)
    }

    @Test
    fun `invoke does not update the MGM's view of group parameters, if approved member has no role set`() {
        val state = RegistrationState(registrationId, member.toAvro(), owner.toAvro(), emptyList())

        val results = handler.invoke(state, key, command)

        verify(membershipPersistenceClient, never()).addNotaryToGroupParameters(persistentMemberInfo)
        verify(groupReaderProvider, times(1)).getGroupReader(any())
        assertThat(results.updatedState).isNull()
        assertThat(results.outputStates)
            .hasSize(4)

        val actionsRequest = results.outputStates.single { it.topic == MEMBERSHIP_ACTIONS_TOPIC }
        val distributeMemberInfo = (actionsRequest.value as? MembershipActionsRequest)?.request as? DistributeMemberInfo
        assertThat(distributeMemberInfo?.minimumGroupParametersEpoch).isEqualTo(5)

        val registrationCommand = results.outputStates.single { it.topic == REGISTRATION_COMMAND_TOPIC }
        val checkForPendingRegistration = (registrationCommand.value as? RegistrationCommand)?.command as? CheckForPendingRegistration
        assertThat(checkForPendingRegistration?.mgm).isEqualTo(owner.toAvro())
        assertThat(checkForPendingRegistration?.member).isEqualTo(member.toAvro())
        assertThat(checkForPendingRegistration?.numberOfRetriesSoFar).isEqualTo(0)
    }

    @Test
    fun `invoke publishes group parameters to kafka if approved member has notary role set `() {
        val state = RegistrationState(registrationId, notary.toAvro(), owner.toAvro(), emptyList())
        val groupParametersCaptor = argumentCaptor<SignedGroupParameters>()
        val holdingIdentityCaptor = argumentCaptor<HoldingIdentity>()

        handler.invoke(state, key, command)

        verify(writerService).put(holdingIdentityCaptor.capture(), groupParametersCaptor.capture())
        assertThat(groupParametersCaptor.firstValue).isEqualTo(mockSignedGroupParameters)
        assertThat(holdingIdentityCaptor.firstValue).isEqualTo(mgm.holdingIdentity)
    }

    @Test
    fun `invoke does not send registration status update message when status cannot be retrieved`() {
        val mockedBuilder = Mockito.mockStatic(VersionedMessageBuilder::class.java).also {
            it.`when`<VersionedMessageBuilder> {
                VersionedMessageBuilder.retrieveRegistrationStatusMessage(any(), any(), any(), any())
            } doReturn null
        }

        val results = handler.invoke(state, key, command)
        verify(
            membershipP2PRecordsFactory,
            never()
        ).createMembershipAuthenticatedMessageRecord(any(), any(), any(), eq(MEMBERSHIP_REGISTRATION_PREFIX), anyOrNull(), any())
        assertThat(results.outputStates)
            .hasSize(3)
        results.outputStates.forEach { assertThat(it.value).isNotInstanceOf(AppMessage::class.java) }

        mockedBuilder.close()
    }

    @Test
    fun `Error is thrown when there is no MGM`() {
        whenever(
            memberTypeChecker.getMgmMemberInfo(owner)
        ).doReturn(null)

        val results = handler.invoke(state, key, command)

        assertThat(results.outputStates)
            .hasSize(1)
            .allSatisfy {
                assertThat(it.topic).isEqualTo(REGISTRATION_COMMAND_TOPIC)
                val value = (it.value as? RegistrationCommand)?.command
                assertThat(value)
                    .isNotNull
                    .isInstanceOf(DeclineRegistration::class.java)
                assertThat((value as? DeclineRegistration)?.reason).isNotBlank()
            }
    }

    @Test
    fun `Error is thrown when the member is not a member`() {
        whenever(
            memberTypeChecker.isMgm(member.toAvro())
        ).doReturn(true)

        val results = handler.invoke(state, key, command)

        assertThat(results.outputStates)
            .hasSize(1)
            .allSatisfy {
                assertThat(it.topic).isEqualTo(REGISTRATION_COMMAND_TOPIC)
                val value = (it.value as? RegistrationCommand)?.command
                assertThat(value)
                    .isNotNull
                    .isInstanceOf(DeclineRegistration::class.java)
                assertThat((value as? DeclineRegistration)?.reason).isNotBlank()
            }
    }

    @Test
    fun `exception is thrown when RegistrationState is null`() {
        assertThrows<MissingRegistrationStateException> {
            handler.invoke(null, key, command)
        }
    }

    @Test
    fun `fails when member name is already in use as notary service name`() {
        val state = RegistrationState(registrationId, member.toAvro(), owner.toAvro(), emptyList())
        val mockNotary = mock<NotaryInfo> {
            on { name } doReturn member.x500Name
        }
        whenever(mockGroupParameters.notaries).doReturn(setOf(mockNotary))

        val results = handler.invoke(state, key, command)

        assertThat(results.outputStates)
            .hasSize(1)
            .allSatisfy {
                assertThat(it.topic).isEqualTo(REGISTRATION_COMMAND_TOPIC)
                val value = (it.value as? RegistrationCommand)?.command
                assertThat(value)
                    .isNotNull
                    .isInstanceOf(DeclineRegistration::class.java)
                assertThat((value as? DeclineRegistration)?.reason).isNotBlank()
            }
    }
}
