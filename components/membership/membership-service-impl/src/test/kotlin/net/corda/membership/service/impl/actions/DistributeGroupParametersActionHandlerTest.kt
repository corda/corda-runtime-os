package net.corda.membership.service.impl.actions

import net.corda.data.membership.actions.request.DistributeGroupParameters
import net.corda.data.membership.actions.request.MembershipActionsRequest
import net.corda.data.membership.p2p.MembershipPackage
import net.corda.data.p2p.app.AppMessage
import net.corda.libs.configuration.SmartConfig
import net.corda.membership.lib.InternalGroupParameters
import net.corda.membership.lib.MemberInfoExtension.Companion.holdingIdentity
import net.corda.membership.p2p.helpers.MembershipPackageFactory
import net.corda.membership.p2p.helpers.Signer
import net.corda.membership.p2p.helpers.SignerFactory
import net.corda.membership.read.MembershipGroupReader
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.records.Record
import net.corda.p2p.messaging.P2pRecordsFactory
import net.corda.p2p.messaging.P2pRecordsFactory.Companion.MEMBERSHIP_DATA_DISTRIBUTION_PREFIX
import net.corda.schema.Schemas
import net.corda.schema.configuration.MembershipConfig
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.virtualnode.toAvro
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DistributeGroupParametersActionHandlerTest {

    private companion object {
        const val EPOCH = 5
        const val MEMBER_INFO_SERIAL = 10L
        const val GROUP_ID = "group"
        const val KEY = "key"
    }

    private val owner = createHoldingIdentity("owner", GROUP_ID)
    private val member = createHoldingIdentity("member", GROUP_ID)
    private val action = DistributeGroupParameters(owner.toAvro(), null)
    private val memberInfo = mockMemberInfo(member, MEMBER_INFO_SERIAL)
    private val mgm = mockMemberInfo(
        createHoldingIdentity("mgm", GROUP_ID),
        MEMBER_INFO_SERIAL,
        isMgm = true,
    )
    private val allActiveMembers = (1..3).map {
        mockMemberInfo(createHoldingIdentity("member-$it", GROUP_ID), MEMBER_INFO_SERIAL)
    } + memberInfo + mgm
    private val activeMembersWithoutMgm = allActiveMembers - mgm
    private val signer = mock<Signer>()
    private val signerFactory = mock<SignerFactory> {
        on { createSigner(mgm) } doReturn signer
    }
    private val record = mock<Record<String, AppMessage>>()
    private val membershipP2PRecordsFactory = mock<P2pRecordsFactory> {
        on {
            createMembershipAuthenticatedMessageRecord(
                any(),
                any(),
                any(),
                eq(MEMBERSHIP_DATA_DISTRIBUTION_PREFIX),
                anyOrNull(),
                any(),
            )
        } doReturn record
    }
    private val membershipPackage = mock<MembershipPackage>()
    private val membershipPackageFactory = mock<MembershipPackageFactory> {
        on {
            createGroupParametersPackage(
                eq(signer),
                any(),
            )
        } doReturn membershipPackage
    }
    private val config = mock<SmartConfig>()

    private val groupParameters: InternalGroupParameters = mock {
        on { epoch } doReturn EPOCH
    }
    private val groupReader: MembershipGroupReader = mock {
        on { groupParameters } doReturn groupParameters
        on { lookup() } doReturn allActiveMembers
    }
    private val groupReaderProvider: MembershipGroupReaderProvider = mock {
        on { getGroupReader(any()) } doReturn groupReader
    }

    private val handler = DistributeGroupParametersActionHandler(
        mock(),
        mock(),
        mock(),
        mock(),
        mock(),
        config,
        groupReaderProvider,
        mock(),
        signerFactory,
        mock(),
        membershipP2PRecordsFactory,
        membershipPackageFactory,
    )

    @Test
    fun `process sends the updated group parameters to all active members over P2P`() {
        val groupParametersPackage = mock<MembershipPackage>()
        whenever(
            membershipPackageFactory.createGroupParametersPackage(
                eq(signer),
                eq(groupParameters),
            )
        ).doReturn(groupParametersPackage)
        val membersRecord = (activeMembersWithoutMgm - memberInfo).map {
            val record = mock<Record<String, AppMessage>>()
            val ownerAvro = owner.toAvro()
            val memberAvro = it.holdingIdentity.toAvro()
            whenever(
                membershipP2PRecordsFactory.createMembershipAuthenticatedMessageRecord(
                    eq(ownerAvro),
                    eq(memberAvro),
                    eq(groupParametersPackage),
                    eq(MEMBERSHIP_DATA_DISTRIBUTION_PREFIX),
                    anyOrNull(),
                    any(),
                )
            ).doReturn(record)
            record
        }

        val reply = handler.process(KEY, action)

        Assertions.assertThat(reply).containsAll(membersRecord)
    }

    @Test
    fun `process republishes the distribute command if expected group parameters are not available via the group reader`() {
        val actionWithEpoch = DistributeGroupParameters(owner.toAvro(), EPOCH + 1)
        val reply = handler.process(KEY, actionWithEpoch)

        Assertions.assertThat(reply)
            .hasSize(1)
            .allSatisfy {
                Assertions.assertThat(it.topic).isEqualTo(Schemas.Membership.MEMBERSHIP_ACTIONS_TOPIC)
                Assertions.assertThat(it.key).isEqualTo(KEY)
                Assertions.assertThat((it.value as? MembershipActionsRequest)?.request).isEqualTo(actionWithEpoch)
            }
    }

    @Test
    fun `process republishes the distribute command if group parameters are not available via the group reader`() {
        whenever(groupReader.groupParameters).thenReturn(null)

        val reply = handler.process(KEY, action)

        Assertions.assertThat(reply)
            .hasSize(1)
            .allSatisfy {
                Assertions.assertThat(it.topic).isEqualTo(Schemas.Membership.MEMBERSHIP_ACTIONS_TOPIC)
                Assertions.assertThat(it.key).isEqualTo(KEY)
                Assertions.assertThat((it.value as? MembershipActionsRequest)?.request).isEqualTo(action)
            }
    }

    @Test
    fun `process republishes the distribute command if creating group parameters package fails`() {
        whenever(membershipPackageFactory.createGroupParametersPackage(any(), any()))
            .thenThrow(CordaRuntimeException(""))

        val reply = handler.process(KEY, action)

        Assertions.assertThat(reply)
            .hasSize(1)
            .allSatisfy {
                Assertions.assertThat(it.topic).isEqualTo(Schemas.Membership.MEMBERSHIP_ACTIONS_TOPIC)
                Assertions.assertThat(it.key).isEqualTo(KEY)
                Assertions.assertThat((it.value as? MembershipActionsRequest)?.request).isEqualTo(action)
            }
    }

    @Test
    fun `process uses the correct TTL configuration`() {
        handler.process(KEY, action)

        verify(config, atLeastOnce()).getIsNull("${MembershipConfig.TtlsConfig.TTLS}.${MembershipConfig.TtlsConfig.MEMBERS_PACKAGE_UPDATE}")
    }
}
