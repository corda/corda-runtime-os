package net.corda.membership.impl.registration.dynamic.handler.mgm

import net.corda.crypto.client.CryptoOpsClient
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.membership.command.registration.mgm.ApproveRegistration
import net.corda.data.membership.common.RegistrationStatus
import net.corda.data.membership.p2p.MembershipPackage
import net.corda.data.membership.p2p.SetOwnRegistrationStatus
import net.corda.data.membership.state.RegistrationState
import net.corda.membership.impl.registration.dynamic.handler.MissingRegistrationStateException
import net.corda.membership.impl.registration.dynamic.handler.helpers.MembershipPackageFactory
import net.corda.membership.impl.registration.dynamic.handler.helpers.MerkleTreeGenerator
import net.corda.membership.impl.registration.dynamic.handler.helpers.P2pRecordsFactory
import net.corda.membership.impl.registration.dynamic.handler.helpers.Signer
import net.corda.membership.impl.registration.dynamic.handler.helpers.SignerFactory
import net.corda.membership.lib.MemberInfoExtension
import net.corda.membership.lib.MemberInfoExtension.Companion.IS_MGM
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_SUSPENDED
import net.corda.membership.lib.MemberInfoExtension.Companion.STATUS
import net.corda.membership.lib.MemberInfoExtension.Companion.groupId
import net.corda.membership.lib.MemberInfoExtension.Companion.holdingIdentity
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipPersistenceResult
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.persistence.client.MembershipQueryResult
import net.corda.messaging.api.records.Record
import net.corda.p2p.app.AppMessage
import net.corda.schema.Schemas.Membership.Companion.MEMBER_LIST_TOPIC
import net.corda.test.util.identity.createTestHoldingIdentity
import net.corda.test.util.time.TestClock
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.merkle.MerkleTree
import net.corda.v5.crypto.merkle.MerkleTreeFactory
import net.corda.v5.membership.MGMContext
import net.corda.v5.membership.MemberContext
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toAvro
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.time.Instant

class ApproveRegistrationHandlerTest {
    private companion object {
        const val GROUP_ID = "group"
    }
    private val owner = createHoldingIdentity("owner")
    private val member = createHoldingIdentity("member")
    private val registrationId = "registrationID"
    private val command = ApproveRegistration()
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
    private val membershipPersistenceClient = mock<MembershipPersistenceClient> {
        on {
            setMemberAndRegistrationRequestAsApproved(
                owner,
                member,
                registrationId
            )
        } doReturn MembershipPersistenceResult.Success(memberInfo)
    }
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
                eq(null)
            )
        } doReturn record
    }
    private val checkHash = mock<SecureHash>()
    private val merkleTree = mock<MerkleTree> {
        on { root } doReturn checkHash
    }
    private val merkleTreeFactory = mock<MerkleTreeFactory>()
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
            )
        } doReturn membershipPackage
    }

    private val handler = ApproveRegistrationHandler(
        membershipPersistenceClient,
        membershipQueryClient,
        cipherSchemeMetadata,
        clock,
        cryptoOpsClient,
        cordaAvroSerializationFactory,
        merkleTreeFactory,
        signerFactory,
        merkleTreeGenerator,
        p2pRecordsFactory,
        membershipPackageFactory,
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
    fun `invoke return all approved members over P2P`() {
        val allMembershipPackage = mock<MembershipPackage>()
        whenever(
            membershipPackageFactory.createMembershipPackage(
                signer,
                signatures,
                activeMembersWithoutMgm,
                checkHash,
            )
        ).doReturn(allMembershipPackage)
        val allMemberPackage = mock<Record<String, AppMessage>>()
        whenever(
            p2pRecordsFactory.createAuthenticatedMessageRecord(
                owner.toAvro(),
                member.toAvro(),
                allMembershipPackage
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
            )
        ).doReturn(memberPackage)
        val membersRecord = (activeMembersWithoutMgm - memberInfo).map {
            val record = mock<Record<String, AppMessage>>()
            whenever(
                p2pRecordsFactory.createAuthenticatedMessageRecord(
                    owner.toAvro(),
                    it.holdingIdentity.toAvro(),
                    memberPackage,
                )
            ).doReturn(record)
            record
        }

        val reply = handler.invoke(state, key, command)

        assertThat(reply.outputStates).containsAll(membersRecord)
    }


    @Test
    fun `invoke sends the approved state to the member over P2P`() {
        val record = mock<Record<String, AppMessage>>()
        whenever(
            p2pRecordsFactory.createAuthenticatedMessageRecord(
                owner.toAvro(),
                member.toAvro(),
                SetOwnRegistrationStatus(
                    registrationId,
                    RegistrationStatus.APPROVED
                )
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
    fun `Error is thrown when there is no MGM`() {
        whenever(membershipQueryClient.queryMemberInfo(owner)).doReturn(MembershipQueryResult.Success(activeMembersWithoutMgm))

        assertThrows<ApproveRegistrationHandler.FailToFindMgm> {
            handler.invoke(state, key, command)
        }
    }

    @Test
    fun `exception is thrown when RegistrationState is null`() {
        assertThrows<MissingRegistrationStateException> {
            handler.invoke(null, key, command)
        }
    }

    private fun mockMemberInfo(
        holdingIdentity: HoldingIdentity,
        isMgm: Boolean = false,
        status: String = MemberInfoExtension.MEMBER_STATUS_ACTIVE,
    ): MemberInfo {
        val mgmContext = mock<MGMContext> {
            on { parseOrNull(eq(IS_MGM), any<Class<Boolean>>()) } doReturn isMgm
            on { parse(eq(STATUS), any<Class<String>>()) } doReturn status
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
