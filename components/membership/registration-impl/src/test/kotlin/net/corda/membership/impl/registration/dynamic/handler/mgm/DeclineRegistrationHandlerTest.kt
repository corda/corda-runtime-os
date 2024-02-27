package net.corda.membership.impl.registration.dynamic.handler.mgm

import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.data.membership.command.registration.mgm.CheckForPendingRegistration
import net.corda.data.membership.command.registration.mgm.DeclineRegistration
import net.corda.data.membership.common.v2.RegistrationStatus
import net.corda.data.membership.p2p.v2.SetOwnRegistrationStatus
import net.corda.data.membership.state.RegistrationState
import net.corda.data.p2p.app.AppMessage
import net.corda.data.p2p.app.MembershipStatusFilter
import net.corda.libs.configuration.SmartConfig
import net.corda.membership.impl.registration.dynamic.handler.MissingRegistrationStateException
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_PENDING
import net.corda.membership.lib.MemberInfoExtension.Companion.status
import net.corda.membership.lib.SelfSignedMemberInfo
import net.corda.membership.lib.VersionedMessageBuilder
import net.corda.membership.p2p.helpers.P2pRecordsFactory
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipPersistenceOperation
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.persistence.client.MembershipQueryResult
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Membership.REGISTRATION_COMMAND_TOPIC
import net.corda.schema.configuration.MembershipConfig.TtlsConfig.DECLINE_REGISTRATION
import net.corda.schema.configuration.MembershipConfig.TtlsConfig.TTLS
import net.corda.test.util.identity.createTestHoldingIdentity
import net.corda.virtualnode.toAvro
import net.corda.virtualnode.toCorda
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DeclineRegistrationHandlerTest {
    private companion object {
        const val GROUP_ID = "ABC123"
        const val REGISTRATION_ID = "REG-01"
        const val TOPIC = "dummyTopic"
        const val PLATFORM_VERSION = 50100
    }

    private val mgm = createTestHoldingIdentity("C=GB, L=London, O=MGM", GROUP_ID).toAvro()
    private val member = createTestHoldingIdentity("C=GB, L=London, O=Alice", GROUP_ID).toAvro()
    private val memberInfo = mock<SelfSignedMemberInfo> {
        on { name } doReturn member.toCorda().x500Name
        on { memberProvidedContext } doReturn mock()
        on { mgmProvidedContext } doReturn mock()
        on { status } doReturn MEMBER_STATUS_PENDING
        on { platformVersion } doReturn PLATFORM_VERSION
    }
    private val command = DeclineRegistration()
    private val state = RegistrationState(
        REGISTRATION_ID,
        member,
        mgm,
        emptyList()
    )
    private val commands = listOf(
        Record(
            "topic",
            "key",
            false,
        )
    )
    private val operation = mock<MembershipPersistenceOperation<Unit>> {
        on { createAsyncCommands() } doReturn commands
    }

    private val membershipPersistenceClient = mock<MembershipPersistenceClient> {
        on {
            setRegistrationRequestStatus(
                mgm.toCorda(),
                REGISTRATION_ID,
                RegistrationStatus.DECLINED
            )
        } doReturn operation
    }

    private val record = mock<Record<String, AppMessage>>()
    private val config = mock<SmartConfig>()
    private val p2pRecordsFactory = mock<P2pRecordsFactory> {
        on {
            createAuthenticatedMessageRecord(
                eq(mgm),
                eq(member),
                eq(
                    SetOwnRegistrationStatus(
                        REGISTRATION_ID,
                        RegistrationStatus.DECLINED,
                        null
                    )
                ),
                any(),
                any(),
                eq(MembershipStatusFilter.PENDING),
            )
        } doReturn record
    }
    private val membershipQueryClient = mock<MembershipQueryClient> {
        on {
            queryMemberInfo(eq(mgm.toCorda()), eq(listOf(member.toCorda())), any())
        } doReturn MembershipQueryResult.Success(listOf(memberInfo))
    }

    private val handler = DeclineRegistrationHandler(
        membershipPersistenceClient,
        membershipQueryClient,
        mock(),
        mock(),
        mock(),
        config,
        p2pRecordsFactory
    )

    @Test
    fun `handler calls persistence client and returns output states`() {
        val expectedOutputStates = listOf(
            record,
            Record(
                topic = REGISTRATION_COMMAND_TOPIC,
                key = member.toString(),
                value = RegistrationCommand(CheckForPendingRegistration(mgm, member, 0))
            )
        ) + commands

        val result = handler.invoke(state, Record(TOPIC, member.toString(), RegistrationCommand(command)))

        verify(membershipPersistenceClient, times(1)).setRegistrationRequestStatus(
            mgm.toCorda(),
            REGISTRATION_ID,
            RegistrationStatus.DECLINED
        )

        assertThat(result.updatedState).isNull()
        assertThat(result.outputStates).containsExactlyInAnyOrderElementsOf(expectedOutputStates)

        val registrationCommand = result.outputStates.single { it.topic == REGISTRATION_COMMAND_TOPIC }
        val checkForPendingRegistration = (registrationCommand.value as? RegistrationCommand)?.command as? CheckForPendingRegistration
        assertThat(checkForPendingRegistration?.mgm).isEqualTo(mgm)
        assertThat(checkForPendingRegistration?.member).isEqualTo(member)
        assertThat(checkForPendingRegistration?.numberOfRetriesSoFar).isEqualTo(0)
    }

    @Test
    fun `handler uses the correct TTL configuration`() {
        handler.invoke(state, Record(TOPIC, member.toString(), RegistrationCommand(command)))

        verify(config).getIsNull("$TTLS.$DECLINE_REGISTRATION")
    }

    @Test
    fun `handler does not send registration status update message when status cannot be retrieved`() {
        val mockedBuilder = Mockito.mockStatic(VersionedMessageBuilder::class.java).also {
            it.`when`<VersionedMessageBuilder> {
                VersionedMessageBuilder.retrieveRegistrationStatusMessage(any(), any(), any(), any())
            } doReturn null
        }

        val results = handler.invoke(state, Record(TOPIC, member.toString(), RegistrationCommand(command)))
        verify(p2pRecordsFactory, never()).createAuthenticatedMessageRecord(any(), any(), any(), anyOrNull(), any(), any())
        assertThat(results.outputStates)
            .hasSize(2)
        results.outputStates.forEach { assertThat(it.value).isNotInstanceOf(AppMessage::class.java) }

        mockedBuilder.close()
    }

    @Test
    fun `exception is thrown when RegistrationState is null`() {
        assertThrows<MissingRegistrationStateException> {
            handler.invoke(null, Record(TOPIC, member.toString(), RegistrationCommand(command)))
        }
    }

    @Test
    fun `update registration status message is not sent when member's pending information cannot be found`() {
        val activeInfo = mock<SelfSignedMemberInfo> {
            on { name } doReturn member.toCorda().x500Name
            on { memberProvidedContext } doReturn mock()
            on { mgmProvidedContext } doReturn mock()
            on { status } doReturn MEMBER_STATUS_ACTIVE
            on { platformVersion } doReturn PLATFORM_VERSION
        }
        whenever(membershipQueryClient.queryMemberInfo(any(), any(), any()))
            .thenReturn(MembershipQueryResult.Success(listOf(activeInfo)))
        handler.invoke(state, Record(TOPIC, member.toString(), RegistrationCommand(command)))
        verify(p2pRecordsFactory, never())
            .createAuthenticatedMessageRecord(any(), any(), any(), anyOrNull(), any(), any())
    }
}
