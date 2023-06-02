package net.corda.membership.service.impl.actions

import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.cipher.suite.merkle.MerkleTreeProvider
import net.corda.crypto.client.CryptoOpsClient
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.membership.actions.request.DistributeMemberInfo
import net.corda.data.membership.actions.request.MembershipActionsRequest
import net.corda.data.membership.p2p.MembershipPackage
import net.corda.data.p2p.app.AppMessage
import net.corda.libs.configuration.SmartConfig
import net.corda.membership.lib.InternalGroupParameters
import net.corda.membership.lib.MemberInfoExtension.Companion.IS_MGM
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_SUSPENDED
import net.corda.membership.lib.MemberInfoExtension.Companion.ROLES_PREFIX
import net.corda.membership.lib.MemberInfoExtension.Companion.STATUS
import net.corda.membership.lib.MemberInfoExtension.Companion.groupId
import net.corda.membership.lib.MemberInfoExtension.Companion.holdingIdentity
import net.corda.membership.lib.MemberInfoExtension.Companion.isMgm
import net.corda.membership.lib.SignedMemberInfo
import net.corda.membership.lib.notary.MemberNotaryDetails
import net.corda.membership.p2p.helpers.MembershipPackageFactory
import net.corda.membership.p2p.helpers.MerkleTreeGenerator
import net.corda.membership.p2p.helpers.P2pRecordsFactory
import net.corda.membership.p2p.helpers.Signer
import net.corda.membership.p2p.helpers.SignerFactory
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.persistence.client.MembershipQueryResult
import net.corda.membership.read.MembershipGroupReader
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import net.corda.schema.configuration.MembershipConfig
import net.corda.test.util.identity.createTestHoldingIdentity
import net.corda.test.util.time.TestClock
import net.corda.utilities.parse
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.merkle.MerkleTree
import net.corda.v5.membership.MGMContext
import net.corda.v5.membership.MemberContext
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
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
import java.nio.ByteBuffer
import java.time.Instant

class DistributeMemberInfoActionHandlerTest {
    private companion object {
        const val EPOCH = 5
        const val MEMBER_INFO_SERIAL = 10L
        const val GROUP_ID = "group"
        const val KEY = "key"
    }
    private fun createHoldingIdentity(name: String): HoldingIdentity {
        return createTestHoldingIdentity("C=GB,L=London,O=$name", GROUP_ID)
    }
    private fun mockMemberInfo(
        holdingIdentity: HoldingIdentity,
        isMgm: Boolean = false,
        status: String = MEMBER_STATUS_ACTIVE,
        isNotary: Boolean = false,
    ): MemberInfo {
        val mgmContext = mock<MGMContext> {
            on { parseOrNull(eq(IS_MGM), any<Class<Boolean>>()) } doReturn isMgm
            on { parse(eq(STATUS), any<Class<String>>()) } doReturn status
            on { entries } doReturn mapOf("mgm" to holdingIdentity.x500Name.toString()).entries
        }
        val memberContext = mock<MemberContext> {
            on { parse(eq(GROUP_ID), any<Class<String>>()) } doReturn holdingIdentity.groupId
            if (isNotary) {
                on { entries } doReturn mapOf(
                    "member" to holdingIdentity.x500Name.toString(),
                    "$ROLES_PREFIX.0" to "notary",
                ).entries
                val notaryDetails = MemberNotaryDetails(
                    holdingIdentity.x500Name,
                    "Notary Plugin A",
                    listOf(1, 2),
                    listOf(mock())
                )
                whenever(mock.parse<MemberNotaryDetails>("corda.notary")).thenReturn(notaryDetails)
            } else {
                on { entries } doReturn mapOf("member" to holdingIdentity.x500Name.toString()).entries
            }
        }
        return mock {
            on { mgmProvidedContext } doReturn mgmContext
            on { memberProvidedContext } doReturn memberContext
            on { name } doReturn holdingIdentity.x500Name
            on { groupId } doReturn holdingIdentity.groupId
            on { serial } doReturn MEMBER_INFO_SERIAL
            on { isActive } doReturn (status == MEMBER_STATUS_ACTIVE)
        }
    }

    private val owner = createHoldingIdentity("owner")
    private val member = createHoldingIdentity("member")
    private val suspendMember = createHoldingIdentity("suspended")
    private val action = DistributeMemberInfo(owner.toAvro(), member.toAvro(), null, null)
    private val distributeSuspendedMemberAction = DistributeMemberInfo(
        owner.toAvro(),
        suspendMember.toAvro(),
        null,
        null
    )
    private val memberInfo = mockMemberInfo(member)
    private val memberInfoWithSignature = listOf(memberInfo).map {
        val name = it.name.toString()
        SignedMemberInfo(
            it,
            CryptoSignatureWithKey(
                ByteBuffer.wrap("pk-$name".toByteArray()),
                ByteBuffer.wrap("sig-$name".toByteArray()),
            ),
            CryptoSignatureSpec("dummy", null, null)
        )
    }
    private val suspendedMemberInfo = mockMemberInfo(suspendMember, status = MEMBER_STATUS_SUSPENDED)
    private val mgm = mockMemberInfo(
        createHoldingIdentity("mgm"),
        isMgm = true,
    )
    private val allActiveMembers = (1..3).map {
        mockMemberInfo(createHoldingIdentity("member-$it"))
    } + memberInfo + mgm
    private val activeMembersWithoutMgm = allActiveMembers - mgm
    private val allActiveMembersWithSignatures = allActiveMembers.map {
        val name = it.name.toString()
        SignedMemberInfo(
            it,
            CryptoSignatureWithKey(
                ByteBuffer.wrap("pk-$name".toByteArray()),
                ByteBuffer.wrap("sig-$name".toByteArray()),
            ),
            CryptoSignatureSpec("dummy", null, null)
        )
    }
    private val activeMembersWithoutMgmWithSignatures = allActiveMembersWithSignatures.filterNot { it.memberInfo.isMgm }
    private val suspendMemberWithSignature = listOf(suspendedMemberInfo).map {
        val name = it.name.toString()
        SignedMemberInfo(
            it,
            CryptoSignatureWithKey(
                ByteBuffer.wrap("pk-$name".toByteArray()),
                ByteBuffer.wrap("sig-$name".toByteArray()),
            ),
            CryptoSignatureSpec("dummy", null, null)
        )
    }
    private val membershipQueryClient = mock<MembershipQueryClient> {
        on { queryMemberInfo(owner, listOf(MEMBER_STATUS_ACTIVE, MEMBER_STATUS_SUSPENDED)) } doReturn MembershipQueryResult.Success(
            allActiveMembersWithSignatures + suspendMemberWithSignature
        )
        on {
            queryMemberInfo(owner, listOf(member), listOf(MEMBER_STATUS_ACTIVE, MEMBER_STATUS_SUSPENDED))
        } doReturn MembershipQueryResult.Success(memberInfoWithSignature)
        on {
            queryMemberInfo(owner, listOf(suspendMember), listOf(MEMBER_STATUS_ACTIVE, MEMBER_STATUS_SUSPENDED))
        } doReturn MembershipQueryResult.Success(suspendMemberWithSignature)
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
    private val p2pRecordsFactory = mock<P2pRecordsFactory> {
        on {
            createAuthenticatedMessageRecord(
                any(),
                any(),
                any(),
                anyOrNull(),
                any(),
                any()
            )
        } doReturn record
    }
    private val checkHash = mock<SecureHash>()
    private val merkleTree = mock<MerkleTree> {
        on { root } doReturn checkHash
    }
    private val merkleTreeProvider = mock<MerkleTreeProvider>()
    private val merkleTreeGenerator = mock<MerkleTreeGenerator> {
        on { generateTree(any()) } doReturn merkleTree
    }
    private val membershipPackage = mock<MembershipPackage>()
    private val membershipPackageFactory = mock<MembershipPackageFactory> {
        on {
            createMembershipPackage(
                eq(signer),
                eq(activeMembersWithoutMgmWithSignatures),
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
        p2pRecordsFactory,
        membershipPackageFactory,
    )

    @Test
    fun `process sends all approved members over P2P`() {
        val allMembershipPackage = mock<MembershipPackage>()
        whenever(
            membershipPackageFactory.createMembershipPackage(
                signer,
                activeMembersWithoutMgmWithSignatures + suspendMemberWithSignature,
                checkHash,
                groupParameters,
            )
        ).doReturn(allMembershipPackage)
        val allMemberPackage = mock<Record<String, AppMessage>>()
        whenever(
            p2pRecordsFactory.createAuthenticatedMessageRecord(
                eq(owner.toAvro()),
                eq(member.toAvro()),
                eq(allMembershipPackage),
                anyOrNull(),
                any(),
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
                suspendMemberWithSignature,
                checkHash,
                groupParameters,
            )
        ).doReturn(onlyOwnMembershipPackage)
        val onlyOwnMemberPackage = mock<Record<String, AppMessage>>()
        whenever(
            p2pRecordsFactory.createAuthenticatedMessageRecord(
                eq(owner.toAvro()),
                eq(member.toAvro()),
                eq(onlyOwnMembershipPackage),
                anyOrNull(),
                any(),
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
                eq(memberInfoWithSignature),
                eq(checkHash),
                eq(groupParameters),
            )
        ).doReturn(memberPackage)
        val membersRecord = (activeMembersWithoutMgm - memberInfo).map {
            val record = mock<Record<String, AppMessage>>()
            val ownerAvro = owner.toAvro()
            val memberAvro = it.holdingIdentity.toAvro()
            whenever(
                p2pRecordsFactory.createAuthenticatedMessageRecord(
                    eq(ownerAvro),
                    eq(memberAvro),
                    eq(memberPackage),
                    anyOrNull(),
                    any(),
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
        whenever(membershipQueryClient.queryMemberInfo(
            owner,
            listOf(member),
            listOf(MEMBER_STATUS_ACTIVE, MEMBER_STATUS_SUSPENDED)
        )).thenReturn(MembershipQueryResult.Success(emptyList()))

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
        whenever(membershipPackageFactory.createMembershipPackage(
            any(),
            eq(activeMembersWithoutMgmWithSignatures + suspendMemberWithSignature),
            any(),
            any()
        )).thenThrow(CordaRuntimeException(""))

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
        whenever(membershipPackageFactory.createMembershipPackage(any(), eq(memberInfoWithSignature), any(), any()))
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