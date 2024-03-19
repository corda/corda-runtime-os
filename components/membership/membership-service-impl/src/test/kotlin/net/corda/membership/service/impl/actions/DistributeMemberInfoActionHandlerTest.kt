package net.corda.membership.service.impl.actions

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.cipher.suite.merkle.MerkleTreeProvider
import net.corda.crypto.client.CryptoOpsClient
import net.corda.data.membership.actions.request.DistributeMemberInfo
import net.corda.data.membership.actions.request.MembershipActionsRequest
import net.corda.data.membership.p2p.MembershipPackage
import net.corda.data.p2p.app.AppMessage
import net.corda.data.p2p.app.MembershipStatusFilter
import net.corda.libs.configuration.SmartConfig
import net.corda.membership.lib.InternalGroupParameters
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_SUSPENDED
import net.corda.membership.lib.MemberInfoExtension.Companion.holdingIdentity
import net.corda.membership.p2p.helpers.MembershipPackageFactory
import net.corda.membership.p2p.helpers.MerkleTreeGenerator
import net.corda.membership.p2p.helpers.Signer
import net.corda.membership.p2p.helpers.SignerFactory
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.persistence.client.MembershipQueryResult
import net.corda.membership.read.MembershipGroupReader
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.records.Record
import net.corda.p2p.messaging.P2pRecordsFactory
import net.corda.p2p.messaging.P2pRecordsFactory.Companion.MEMBERSHIP_DATA_DISTRIBUTION_PREFIX
import net.corda.schema.Schemas
import net.corda.schema.configuration.MembershipConfig
import net.corda.test.util.time.TestClock
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.merkle.MerkleTree
import net.corda.virtualnode.toAvro
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

class DistributeMemberInfoActionHandlerTest {
    private companion object {
        const val EPOCH = 5
        const val MEMBER_INFO_SERIAL = 10L
        const val GROUP_ID = "group"
        const val KEY = "key"
    }

    private val owner = createHoldingIdentity("owner", GROUP_ID)
    private val member = createHoldingIdentity("member", GROUP_ID)
    private val suspendMember = createHoldingIdentity("suspended", GROUP_ID)
    private val action = DistributeMemberInfo(owner.toAvro(), member.toAvro(), null, null)
    private val distributeSuspendedMemberAction = DistributeMemberInfo(
        owner.toAvro(),
        suspendMember.toAvro(),
        null,
        null
    )
    private val memberInfo = mockSignedMemberInfo(member, MEMBER_INFO_SERIAL)
    private val suspendedMemberInfo = mockSignedMemberInfo(suspendMember, MEMBER_INFO_SERIAL, status = MEMBER_STATUS_SUSPENDED)
    private val mgm = mockSignedMemberInfo(
        createHoldingIdentity("mgm", GROUP_ID),
        MEMBER_INFO_SERIAL,
        isMgm = true,
    )
    private val allActiveMembers = (1..3).map {
        mockSignedMemberInfo(createHoldingIdentity("member-$it", GROUP_ID), MEMBER_INFO_SERIAL)
    } + memberInfo + mgm
    private val activeMembersWithoutMgm = allActiveMembers - mgm
    private val membershipQueryClient = mock<MembershipQueryClient> {
        on { queryMemberInfo(owner, listOf(MEMBER_STATUS_ACTIVE, MEMBER_STATUS_SUSPENDED)) } doReturn MembershipQueryResult.Success(
            allActiveMembers + suspendedMemberInfo
        )
        on {
            queryMemberInfo(owner, listOf(member), listOf(MEMBER_STATUS_ACTIVE, MEMBER_STATUS_SUSPENDED))
        } doReturn MembershipQueryResult.Success(listOf(memberInfo))
        on {
            queryMemberInfo(owner, listOf(suspendMember), listOf(MEMBER_STATUS_ACTIVE, MEMBER_STATUS_SUSPENDED))
        } doReturn MembershipQueryResult.Success(listOf(suspendedMemberInfo))
    }
    private val cipherSchemeMetadata = mock<CipherSchemeMetadata>()
    private val clock = TestClock(Instant.ofEpochMilli(0))
    private val cryptoOpsClient = mock<CryptoOpsClient>()
    private val cordaAvroSerializationFactory = mock<CordaAvroSerializationFactory>()
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
    private val checkHash = mock<SecureHash>()
    private val merkleTree = mock<MerkleTree> {
        on { root } doReturn checkHash
    }
    private val merkleTreeProvider = mock<MerkleTreeProvider>()
    private val merkleTreeGenerator = mock<MerkleTreeGenerator> {
        on { generateTreeUsingSignedMembers(any()) } doReturn merkleTree
    }
    private val membershipPackage = mock<MembershipPackage>()
    private val membershipPackageFactory = mock<MembershipPackageFactory> {
        on {
            createMembershipPackage(
                eq(signer),
                eq(activeMembersWithoutMgm + suspendedMemberInfo),
                any(),
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
        on { lookup(MembershipStatusFilter.ACTIVE_OR_SUSPENDED) } doReturn allActiveMembers + suspendedMemberInfo
    }
    private val groupReaderProvider: MembershipGroupReaderProvider = mock {
        on { getGroupReader(any()) } doReturn groupReader
    }

    private val handler = DistributeMemberInfoActionHandler(
        membershipQueryClient,
        cipherSchemeMetadata,
        clock,
        cryptoOpsClient,
        cordaAvroSerializationFactory,
        merkleTreeProvider,
        config,
        groupReaderProvider,
        mock(),
        signerFactory,
        merkleTreeGenerator,
        membershipP2PRecordsFactory,
        membershipPackageFactory,
    )

    @Test
    fun `process sends all approved members over P2P`() {
        val allMembershipPackage = mock<MembershipPackage>()
        whenever(
            membershipPackageFactory.createMembershipPackage(
                signer,
                activeMembersWithoutMgm + suspendedMemberInfo,
                checkHash,
                groupParameters,
            )
        ).doReturn(allMembershipPackage)
        val allMemberPackage = mock<Record<String, AppMessage>>()
        whenever(
            membershipP2PRecordsFactory.createMembershipAuthenticatedMessageRecord(
                eq(owner.toAvro()),
                eq(member.toAvro()),
                eq(allMembershipPackage),
                eq(MEMBERSHIP_DATA_DISTRIBUTION_PREFIX),
                anyOrNull(),
                any(),
            )
        ).doReturn(allMemberPackage)

        val reply = handler.process(KEY, action)

        assertThat(reply).contains(allMemberPackage)
    }

    @Test
    fun `process sends only own member info to suspended member over P2P`() {
        val onlyOwnMembershipPackage = mock<MembershipPackage>()
        whenever(
            membershipPackageFactory.createMembershipPackage(
                signer,
                listOf(suspendedMemberInfo),
                checkHash,
                groupParameters,
            )
        ).doReturn(onlyOwnMembershipPackage)
        val onlyOwnMemberPackage = mock<Record<String, AppMessage>>()
        whenever(
            membershipP2PRecordsFactory.createMembershipAuthenticatedMessageRecord(
                eq(owner.toAvro()),
                eq(member.toAvro()),
                eq(onlyOwnMembershipPackage),
                eq(MEMBERSHIP_DATA_DISTRIBUTION_PREFIX),
                anyOrNull(),
                any(),
            )
        ).doReturn(onlyOwnMemberPackage)

        val reply = handler.process(KEY, distributeSuspendedMemberAction)

        assertThat(reply).contains(onlyOwnMemberPackage)
    }

    @Test
    fun `process sends the updated member to all other members over P2P`() {
        val memberPackage = mock<MembershipPackage>()
        whenever(
            membershipPackageFactory.createMembershipPackage(
                eq(signer),
                eq(listOf(memberInfo)),
                eq(checkHash),
                eq(groupParameters),
            )
        ).doReturn(memberPackage)
        val membersRecord = (activeMembersWithoutMgm - memberInfo).map {
            val record = mock<Record<String, AppMessage>>()
            val ownerAvro = owner.toAvro()
            val memberAvro = it.holdingIdentity.toAvro()
            whenever(
                membershipP2PRecordsFactory.createMembershipAuthenticatedMessageRecord(
                    eq(ownerAvro),
                    eq(memberAvro),
                    eq(memberPackage),
                    eq(MEMBERSHIP_DATA_DISTRIBUTION_PREFIX),
                    anyOrNull(),
                    any(),
                )
            ).doReturn(record)
            record
        }

        val reply = handler.process(KEY, action)

        assertThat(reply).containsAll(membersRecord)
    }

    @Test
    fun `process republishes the distribute command if no member info is available via the group reader`() {
        whenever(
            membershipQueryClient.queryMemberInfo(
                owner,
                listOf(member),
                listOf(MEMBER_STATUS_ACTIVE, MEMBER_STATUS_SUSPENDED)
            )
        ).thenReturn(MembershipQueryResult.Success(emptyList()))

        val reply = handler.process(KEY, action)

        assertThat(reply)
            .hasSize(1)
            .allSatisfy {
                assertThat(it.topic).isEqualTo(Schemas.Membership.MEMBERSHIP_ACTIONS_TOPIC)
                assertThat(it.key).isEqualTo(KEY)
                assertThat((it.value as? MembershipActionsRequest)?.request).isEqualTo(action)
            }
    }

    @Test
    fun `process republishes the distribute command if expected member info version is not available via the group reader`() {
        val actionWithSerial = DistributeMemberInfo(owner.toAvro(), member.toAvro(), null, MEMBER_INFO_SERIAL + 1)
        val reply = handler.process(KEY, actionWithSerial)

        assertThat(reply)
            .hasSize(1)
            .allSatisfy {
                assertThat(it.topic).isEqualTo(Schemas.Membership.MEMBERSHIP_ACTIONS_TOPIC)
                assertThat(it.key).isEqualTo(KEY)
                assertThat((it.value as? MembershipActionsRequest)?.request).isEqualTo(actionWithSerial)
            }
    }

    @Test
    fun `process republishes the distribute command if expected group parameters are not available via the group reader`() {
        val actionWithEpoch = DistributeMemberInfo(owner.toAvro(), member.toAvro(), EPOCH + 1, null)
        val reply = handler.process(KEY, actionWithEpoch)

        assertThat(reply)
            .hasSize(1)
            .allSatisfy {
                assertThat(it.topic).isEqualTo(Schemas.Membership.MEMBERSHIP_ACTIONS_TOPIC)
                assertThat(it.key).isEqualTo(KEY)
                assertThat((it.value as? MembershipActionsRequest)?.request).isEqualTo(actionWithEpoch)
            }
    }

    @Test
    fun `process republishes the distribute command if no group parameters is available via the group reader`() {
        whenever(groupReader.groupParameters).thenReturn(null)

        val reply = handler.process(KEY, action)

        assertThat(reply)
            .hasSize(1)
            .allSatisfy {
                assertThat(it.topic).isEqualTo(Schemas.Membership.MEMBERSHIP_ACTIONS_TOPIC)
                assertThat(it.key).isEqualTo(KEY)
                assertThat((it.value as? MembershipActionsRequest)?.request).isEqualTo(action)
            }
    }

    @Test
    fun `process republishes the distribute command if query member signature fails`() {
        whenever(membershipQueryClient.queryMemberInfo(any(), any(), any())).thenReturn(
            MembershipQueryResult.Failure("An error happened.")
        )

        val reply = handler.process(KEY, action)

        assertThat(reply)
            .hasSize(1)
            .allSatisfy {
                assertThat(it.topic).isEqualTo(Schemas.Membership.MEMBERSHIP_ACTIONS_TOPIC)
                assertThat(it.key).isEqualTo(KEY)
                assertThat((it.value as? MembershipActionsRequest)?.request).isEqualTo(action)
            }
    }

    @Test
    fun `process republishes the distribute command if creating membership package to send to updated member fails`() {
        whenever(
            membershipPackageFactory.createMembershipPackage(
                any(),
                eq(activeMembersWithoutMgm + suspendedMemberInfo),
                any(),
                any()
            )
        ).thenThrow(CordaRuntimeException(""))

        val reply = handler.process(KEY, action)

        assertThat(reply)
            .hasSize(1)
            .allSatisfy {
                assertThat(it.topic).isEqualTo(Schemas.Membership.MEMBERSHIP_ACTIONS_TOPIC)
                assertThat(it.key).isEqualTo(KEY)
                assertThat((it.value as? MembershipActionsRequest)?.request).isEqualTo(action)
            }
    }

    @Test
    fun `process republishes the distribute command if create membership package to send to all other member fails`() {
        whenever(membershipPackageFactory.createMembershipPackage(any(), eq(listOf(memberInfo)), any(), any()))
            .thenThrow(CordaRuntimeException(""))

        val reply = handler.process(KEY, action)

        assertThat(reply)
            .hasSize(1)
            .allSatisfy {
                assertThat(it.topic).isEqualTo(Schemas.Membership.MEMBERSHIP_ACTIONS_TOPIC)
                assertThat(it.key).isEqualTo(KEY)
                assertThat((it.value as? MembershipActionsRequest)?.request).isEqualTo(action)
            }
    }

    @Test
    fun `process uses the correct TTL configuration`() {
        handler.process(KEY, action)

        verify(config, atLeastOnce()).getIsNull("${MembershipConfig.TtlsConfig.TTLS}.${MembershipConfig.TtlsConfig.MEMBERS_PACKAGE_UPDATE}")
    }
}
