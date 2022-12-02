package net.corda.membership.impl.registration.dynamic.handler.mgm

import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.cipher.suite.merkle.MerkleTreeProvider
import net.corda.crypto.client.CryptoOpsClient
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.data.membership.command.registration.mgm.DistributeMembershipPackage
import net.corda.data.membership.p2p.MembershipPackage
import net.corda.data.membership.state.RegistrationState
import net.corda.libs.configuration.SmartConfig
import net.corda.membership.impl.registration.dynamic.handler.MissingRegistrationStateException
import net.corda.membership.impl.registration.dynamic.handler.TestUtils.createHoldingIdentity
import net.corda.membership.impl.registration.dynamic.handler.TestUtils.mockMemberInfo
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_SUSPENDED
import net.corda.membership.lib.MemberInfoExtension.Companion.holdingIdentity
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
import net.corda.p2p.app.AppMessage
import net.corda.schema.Schemas.Membership.Companion.REGISTRATION_COMMAND_TOPIC
import net.corda.schema.configuration.MembershipConfig.TtlsConfig.MEMBERS_PACKAGE_UPDATE
import net.corda.schema.configuration.MembershipConfig.TtlsConfig.TTLS
import net.corda.test.util.time.TestClock
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.merkle.MerkleTree
import net.corda.v5.membership.GroupParameters
import net.corda.virtualnode.toAvro
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.time.Instant

class DistributeMembershipPackageHandlerTest {
    private companion object {
        const val EPOCH = 5
    }
    private val owner = createHoldingIdentity("owner")
    private val member = createHoldingIdentity("member")
    private val registrationId = "registrationID"
    private val command = DistributeMembershipPackage(EPOCH)
    private val state = RegistrationState(registrationId, member.toAvro(), owner.toAvro())
    private val key = "key"
    private val memberInfo = mockMemberInfo(member)
    private val inactiveMember = mockMemberInfo(
        createHoldingIdentity("inactive"),
        status = MEMBER_STATUS_SUSPENDED
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
    private val membershipQueryClient = mock<MembershipQueryClient> {
        on { queryMemberInfo(owner) } doReturn MembershipQueryResult.Success(allActiveMembers + inactiveMember)
        on {
            queryMembersSignatures(
                mgm.holdingIdentity,
                activeMembersWithoutMgm.map { it.holdingIdentity },
            )
        } doReturn MembershipQueryResult.Success(
            signatures
        )
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
                eq(signatures),
                any(),
                any(),
                any(),
            )
        } doReturn membershipPackage
    }
    private val config = mock<SmartConfig>()

    private val groupParameters: GroupParameters = mock {
        on { epoch } doReturn EPOCH
    }
    private val groupReader: MembershipGroupReader = mock {
        on { groupParameters } doReturn groupParameters
        on { lookup() } doReturn allActiveMembers
    }
    private val groupReaderProvider: MembershipGroupReaderProvider = mock {
        on { getGroupReader(any()) } doReturn groupReader
    }

    private val handler = DistributeMembershipPackageHandler(
        membershipQueryClient,
        cipherSchemeMetadata,
        clock,
        cryptoOpsClient,
        cordaAvroSerializationFactory,
        merkleTreeProvider,
        config,
        groupReaderProvider,
        signerFactory,
        merkleTreeGenerator,
        p2pRecordsFactory,
        membershipPackageFactory,
    )

    @Test
    fun `invoke returns all approved members over P2P`() {
        val allMembershipPackage = mock<MembershipPackage>()
        whenever(
            membershipPackageFactory.createMembershipPackage(
                signer,
                signatures,
                activeMembersWithoutMgm,
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
            )
        ).doReturn(allMemberPackage)

        val reply = handler.invoke(state, key, command)

        assertThat(reply.outputStates).contains(allMemberPackage)
    }

    @Test
    fun `invoke sends the newly approved member to all other members over P2P`() {
        val memberPackage = mock<MembershipPackage>()
        whenever(
            membershipPackageFactory.createMembershipPackage(
                eq(signer),
                eq(signatures),
                argThat {
                    this.size == 1
                },
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
                )
            ).doReturn(record)
            record
        }

        val reply = handler.invoke(state, key, command)

        assertThat(reply.outputStates).containsAll(membersRecord)
    }

    @Test
    fun `invoke republishes the distribute command if expected group parameters are not available via the group reader`() {
        whenever(groupParameters.epoch).thenReturn(EPOCH - 1)

        val reply = handler.invoke(state, key, command)

        assertThat(reply.outputStates)
            .hasSize(1)
            .allSatisfy {
                assertThat(it.topic).isEqualTo(REGISTRATION_COMMAND_TOPIC)
                assertThat((it.value as? RegistrationCommand)?.command)
                    .isNotNull
                    .isInstanceOf(DistributeMembershipPackage::class.java)
            }
    }

    @Test
    fun `invoke republishes the distribute command on exception`() {
        whenever(groupReader.lookup()).thenThrow(CordaRuntimeException("test"))

        val reply = handler.invoke(state, key, command)

        assertThat(reply.outputStates)
            .hasSize(1)
            .allSatisfy {
                assertThat(it.topic).isEqualTo(REGISTRATION_COMMAND_TOPIC)
                assertThat((it.value as? RegistrationCommand)?.command)
                    .isNotNull
                    .isInstanceOf(DistributeMembershipPackage::class.java)
            }
    }

    @Test
    fun `invoke uses the correct TTL configuration`() {
        handler.invoke(state, key, command)

        verify(config, atLeastOnce()).getIsNull("$TTLS.$MEMBERS_PACKAGE_UPDATE")
    }

    @Test
    fun `exception is thrown when RegistrationState is null`() {
        assertThrows<MissingRegistrationStateException> {
            handler.invoke(null, key, command)
        }
    }
}
