package net.corda.membership.impl.registration.dynamic.handler.mgm

import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.data.membership.command.registration.mgm.ApproveRegistration
import net.corda.data.membership.command.registration.mgm.DeclineRegistration
import net.corda.data.membership.command.registration.mgm.DistributeMembershipPackage
import net.corda.data.membership.common.RegistrationStatus
import net.corda.data.membership.p2p.SetOwnRegistrationStatus
import net.corda.data.membership.state.RegistrationState
import net.corda.membership.groupparams.writer.service.GroupParametersWriterService
import net.corda.membership.impl.registration.dynamic.handler.MemberTypeChecker
import net.corda.membership.impl.registration.dynamic.handler.MissingRegistrationStateException
import net.corda.membership.impl.registration.dynamic.handler.TestUtils.createHoldingIdentity
import net.corda.membership.impl.registration.dynamic.handler.TestUtils.mockMemberInfo
import net.corda.membership.lib.EPOCH_KEY
import net.corda.membership.lib.GroupParametersFactory
import net.corda.membership.lib.MemberInfoExtension.Companion.holdingIdentity
import net.corda.membership.p2p.helpers.P2pRecordsFactory
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipPersistenceResult
import net.corda.membership.read.MembershipGroupReader
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.records.Record
import net.corda.data.p2p.app.AppMessage
import net.corda.schema.Schemas.Membership.MEMBER_LIST_TOPIC
import net.corda.schema.Schemas.Membership.REGISTRATION_COMMAND_TOPIC
import net.corda.test.util.time.TestClock
import net.corda.v5.membership.GroupParameters
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toAvro
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
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
    private val state = RegistrationState(registrationId, member.toAvro(), owner.toAvro())
    private val key = "key"
    private val mockGroupParametersList = KeyValuePairList(
        listOf(
            KeyValuePair(EPOCH_KEY, "5")
        )
    )
    private val memberInfo = mockMemberInfo(member)
    private val notaryInfo = mockMemberInfo(notary, isNotary = true)
    private val mgm = mockMemberInfo(
        createHoldingIdentity("mgm"),
        isMgm = true,
    )
    private val membershipPersistenceClient = mock<MembershipPersistenceClient> {
        on {
            setMemberAndRegistrationRequestAsApproved(
                owner,
                member,
                registrationId
            )
        } doReturn MembershipPersistenceResult.Success(memberInfo)
        on {
            setMemberAndRegistrationRequestAsApproved(
                owner,
                notary,
                registrationId
            )
        } doReturn MembershipPersistenceResult.Success(notaryInfo)
        on {
            addNotaryToGroupParameters(
                mgm.holdingIdentity,
                notaryInfo
            )
        } doReturn MembershipPersistenceResult.Success(mockGroupParametersList)
    }
    private val clock = TestClock(Instant.ofEpochMilli(0))
    private val cordaAvroSerializationFactory = mock<CordaAvroSerializationFactory>()
    private val record = mock<Record<String, AppMessage>>()
    private val p2pRecordsFactory = mock<P2pRecordsFactory> {
        on {
            createAuthenticatedMessageRecord(
                any(),
                any(),
                any(),
                anyOrNull(),
                any(),
            )
        } doReturn record
    }
    private val memberTypeChecker = mock<MemberTypeChecker> {
        on { isMgm(member.toAvro()) } doReturn false
        on { getMgmMemberInfo(owner) } doReturn mgm
    }
    private val mockGroupParameters: GroupParameters = mock {
        on { epoch } doReturn 5
    }
    private val groupReader: MembershipGroupReader = mock {
        on { groupParameters } doReturn mockGroupParameters
    }
    private val groupReaderProvider: MembershipGroupReaderProvider = mock {
        on { getGroupReader(any()) } doReturn groupReader
    }
    private val writerService: GroupParametersWriterService = mock()
    private val persistedGroupParameters: GroupParameters = mock {
        on { epoch } doReturn 6
    }
    private val groupParametersFactory: GroupParametersFactory = mock {
        on { create(any<KeyValuePairList>()) } doReturn persistedGroupParameters
    }

    private val handler = ApproveRegistrationHandler(
        membershipPersistenceClient,
        clock,
        cordaAvroSerializationFactory,
        memberTypeChecker,
        groupReaderProvider,
        writerService,
        groupParametersFactory,
        p2pRecordsFactory,
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
                assertThat(value?.viewOwningMember).isEqualTo(owner.toAvro())
                assertThat(value?.memberContext?.items).contains(
                    KeyValuePair(
                        "member",
                        member.x500Name.toString(),
                    )
                )
                assertThat(value?.mgmContext?.items).contains(
                    KeyValuePair(
                        "mgm",
                        member.x500Name.toString(),
                    )
                )
            }
    }

    @Test
    fun `invoke sends the approved state to the member over P2P`() {
        val record = mock<Record<String, AppMessage>>()
        whenever(
            p2pRecordsFactory.createAuthenticatedMessageRecord(
                eq(owner.toAvro()),
                eq(member.toAvro()),
                eq(
                    SetOwnRegistrationStatus(
                        registrationId,
                        RegistrationStatus.APPROVED
                    )
                ),
                anyOrNull(),
                any(),
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
        val state = RegistrationState(registrationId, notary.toAvro(), owner.toAvro())

        val results = handler.invoke(state, key, command)

        verify(membershipPersistenceClient).addNotaryToGroupParameters(
            viewOwningIdentity = mgm.holdingIdentity,
            notary = notaryInfo,
        )
        verify(groupReaderProvider, never()).getGroupReader(any())
        assertThat(results.outputStates)
            .hasSize(3)
            .anySatisfy {
                assertThat(it.topic).isEqualTo(REGISTRATION_COMMAND_TOPIC)
                val value = (it.value as? RegistrationCommand)?.command
                assertThat(value)
                    .isNotNull
                    .isInstanceOf(DistributeMembershipPackage::class.java)
                assertThat((value as? DistributeMembershipPackage)?.groupParametersEpoch).isEqualTo(6)
            }
    }

    @Test
    fun `invoke does not update the MGM's view of group parameters, if approved member has no role set`() {
        val state = RegistrationState(registrationId, member.toAvro(), owner.toAvro())

        val results = handler.invoke(state, key, command)

        verify(membershipPersistenceClient, never()).addNotaryToGroupParameters(
            viewOwningIdentity = mgm.holdingIdentity,
            notary = memberInfo,
        )
        verify(groupReaderProvider, times(1)).getGroupReader(any())
        assertThat(results.outputStates)
            .hasSize(3)
            .anySatisfy {
                assertThat(it.topic).isEqualTo(REGISTRATION_COMMAND_TOPIC)
                val value = (it.value as? RegistrationCommand)?.command
                assertThat(value)
                    .isNotNull
                    .isInstanceOf(DistributeMembershipPackage::class.java)
                assertThat((value as? DistributeMembershipPackage)?.groupParametersEpoch).isEqualTo(5)
            }
    }

    @Test
    fun `invoke publishes group parameters to kafka if approved member has notary role set `() {
        val state = RegistrationState(registrationId, notary.toAvro(), owner.toAvro())
        val groupParametersCaptor = argumentCaptor<GroupParameters>()
        val holdingIdentityCaptor = argumentCaptor<HoldingIdentity>()

        handler.invoke(state, key, command)

        verify(writerService).put(holdingIdentityCaptor.capture(), groupParametersCaptor.capture())
        assertThat(groupParametersCaptor.firstValue).isEqualTo(persistedGroupParameters)
        assertThat(holdingIdentityCaptor.firstValue).isEqualTo(mgm.holdingIdentity)
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
}
