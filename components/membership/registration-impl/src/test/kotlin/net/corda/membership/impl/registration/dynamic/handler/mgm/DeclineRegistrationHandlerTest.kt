package net.corda.membership.impl.registration.dynamic.handler.mgm

import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.data.membership.command.registration.mgm.DeclineRegistration
import net.corda.data.membership.state.RegistrationState
import net.corda.membership.impl.registration.dynamic.handler.MissingRegistrationStateException
import net.corda.membership.lib.MemberInfoExtension
import net.corda.membership.lib.MemberInfoExtension.Companion.groupId
import net.corda.membership.lib.MemberInfoExtension.Companion.holdingIdentity
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipPersistenceResult
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.persistence.client.MembershipQueryResult
import net.corda.messaging.api.records.Record
import net.corda.test.util.identity.createTestHoldingIdentity
import net.corda.v5.membership.MGMContext
import net.corda.v5.membership.MemberContext
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toAvro
import net.corda.virtualnode.toCorda
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.nio.ByteBuffer

class DeclineRegistrationHandlerTest {
    private companion object {
        const val GROUP_ID = "ABC123"
        const val REGISTRATION_ID = "REG-01"
        const val TOPIC = "dummyTopic"
    }

    private val owner = createTestHoldingIdentity("C=GB, L=London, O=mgm", GROUP_ID).toAvro()
    private val member = createTestHoldingIdentity("C=GB, L=London, O=member", GROUP_ID).toAvro()
    private val command = DeclineRegistration()
    private val state = RegistrationState(
        REGISTRATION_ID,
        member,
        owner
    )

    private val memberInfo = mockMemberInfo(member.toCorda())

    private val inactiveMember = mockMemberInfo(
        createHoldingIdentity("inactive"),
        status = MemberInfoExtension.MEMBER_STATUS_SUSPENDED
    )

    private val mgm = mockMemberInfo(
        createHoldingIdentity("mgm"),
        isMgm = true,
    )

    private val allActiveMembers = (1..3).map {
        mockMemberInfo(createHoldingIdentity("member-$it"))
    } + memberInfo + mgm

    private val activeMembersWithoutMgm = allActiveMembers - mgm

    private val signatures = activeMembersWithoutMgm.associate {
        val name = it.name.toString()
        it.holdingIdentity to CryptoSignatureWithKey(
            ByteBuffer.wrap("pk-$name".toByteArray()),
            ByteBuffer.wrap("sig-$name".toByteArray()),
            KeyValuePairList(
                listOf(
                    KeyValuePair("name", name)
                )
            )
        )
    }

    private val membershipPersistenceClient = mock<MembershipPersistenceClient> {
        on {
            setMemberAndRegistrationRequestAsDeclined(
                owner.toCorda(),
                member.toCorda(),
                REGISTRATION_ID
            )
        } doReturn MembershipPersistenceResult.success()
    }

    private val membershipQueryClient = mock<MembershipQueryClient> {
        on { queryMemberInfo(owner.toCorda()) } doReturn MembershipQueryResult.Success(allActiveMembers + inactiveMember)
        on {
            queryMembersSignatures(
                mgm.holdingIdentity,
                activeMembersWithoutMgm.map { it.holdingIdentity },
            )
        } doReturn MembershipQueryResult.Success(
            signatures
        )
    }

    private val handler = DeclineRegistrationHandler(membershipPersistenceClient, membershipQueryClient)

    @Test
    fun `handler calls persistence client and returns no output states`() {
        val result = handler.invoke(state, Record(TOPIC, member.toString(), RegistrationCommand(command)))

        verify(membershipPersistenceClient, times(1)).setMemberAndRegistrationRequestAsDeclined(
            owner.toCorda(),
            member.toCorda(),
            REGISTRATION_ID
        )

        Assertions.assertThat(result.outputStates).hasSize(0)
        with(result.updatedState) {
            Assertions.assertThat(this?.registeringMember).isEqualTo(member)
            Assertions.assertThat(this?.mgm).isEqualTo(owner)
            Assertions.assertThat(this?.registrationId).isEqualTo(REGISTRATION_ID)
        }
    }

    @Test
    fun `exception is thrown when RegistrationState is null`() {
        assertThrows<MissingRegistrationStateException> {
            handler.invoke(null, Record(TOPIC, member.toString(), RegistrationCommand(command)))
        }
    }

    private fun mockMemberInfo(
        holdingIdentity: HoldingIdentity,
        isMgm: Boolean = false,
        status: String = MemberInfoExtension.MEMBER_STATUS_ACTIVE,
    ): MemberInfo {
        val mgmContext = mock<MGMContext> {
            on { parseOrNull(eq(MemberInfoExtension.IS_MGM), any<Class<Boolean>>()) } doReturn isMgm
            on { parse(eq(MemberInfoExtension.STATUS), any<Class<String>>()) } doReturn status
            on { entries } doReturn mapOf("mgm" to holdingIdentity.x500Name.toString()).entries
        }
        val memberContext = mock<MemberContext> {
            on { parse(eq(MemberInfoExtension.GROUP_ID), any<Class<String>>()) } doReturn holdingIdentity.groupId
            on { entries } doReturn mapOf("member" to holdingIdentity.x500Name.toString()).entries
        }
        return mock {
            on { mgmProvidedContext } doReturn mgmContext
            on { memberProvidedContext } doReturn memberContext
            on { name } doReturn holdingIdentity.x500Name
            on { groupId } doReturn holdingIdentity.groupId
        }
    }

    private fun createHoldingIdentity(name: String): HoldingIdentity {
        return createTestHoldingIdentity("C=GB,L=London,O=$name", GROUP_ID)
    }

}
