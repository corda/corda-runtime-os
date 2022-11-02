package net.corda.membership.impl.registration.dynamic.handler.mgm

import net.corda.crypto.client.CryptoOpsClient
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.data.membership.command.registration.mgm.ApproveRegistration
import net.corda.data.membership.command.registration.mgm.DeclineRegistration
import net.corda.data.membership.common.RegistrationStatus
import net.corda.data.membership.p2p.MembershipPackage
import net.corda.data.membership.p2p.SetOwnRegistrationStatus
import net.corda.data.membership.state.RegistrationState
import net.corda.libs.configuration.SmartConfig
import net.corda.membership.impl.registration.dynamic.handler.MemberTypeChecker
import net.corda.membership.impl.registration.dynamic.handler.MissingRegistrationStateException
import net.corda.membership.lib.MemberInfoExtension
import net.corda.membership.lib.MemberInfoExtension.Companion.IS_MGM
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_SUSPENDED
import net.corda.membership.lib.MemberInfoExtension.Companion.ROLES_PREFIX
import net.corda.membership.lib.MemberInfoExtension.Companion.STATUS
import net.corda.membership.lib.MemberInfoExtension.Companion.groupId
import net.corda.membership.lib.MemberInfoExtension.Companion.holdingIdentity
import net.corda.membership.lib.notary.MemberNotaryDetails
import net.corda.membership.p2p.helpers.MembershipPackageFactory
import net.corda.membership.p2p.helpers.MerkleTreeGenerator
import net.corda.membership.p2p.helpers.P2pRecordsFactory
import net.corda.membership.p2p.helpers.Signer
import net.corda.membership.p2p.helpers.SignerFactory
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipPersistenceResult
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.persistence.client.MembershipQueryResult
import net.corda.messaging.api.records.Record
import net.corda.p2p.app.AppMessage
import net.corda.schema.Schemas.Membership.Companion.MEMBER_LIST_TOPIC
import net.corda.schema.Schemas.Membership.Companion.REGISTRATION_COMMAND_TOPIC
import net.corda.schema.configuration.MembershipConfig.TtlsConfig.MEMBERS_PACKAGE_UPDATE
import net.corda.schema.configuration.MembershipConfig.TtlsConfig.TTLS
import net.corda.test.util.identity.createTestHoldingIdentity
import net.corda.test.util.time.TestClock
import net.corda.v5.base.util.parse
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.merkle.MerkleTreeProvider
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.merkle.MerkleTree
import net.corda.v5.membership.MGMContext
import net.corda.v5.membership.MemberContext
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
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

class ApproveRegistrationHandlerTest {
    private companion object {
        const val GROUP_ID = "group"
    }
    private val owner = createHoldingIdentity("owner")
    private val member = createHoldingIdentity("member")
    private val notary = createHoldingIdentity("notary")
    private val registrationId = "registrationID"
    private val command = ApproveRegistration()
    private val state = RegistrationState(registrationId, member.toAvro(), owner.toAvro())
    private val key = "key"
    private val memberInfo = mockMemberInfo(member)
    private val notaryInfo = mockMemberInfo(notary, isNotary = true)
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
        on {
            setMemberAndRegistrationRequestAsApproved(
                owner,
                notary,
                registrationId
            )
        } doReturn MembershipPersistenceResult.Success(notaryInfo)
        on { addNotaryToGroupParameters(mgm.holdingIdentity, notaryInfo) } doReturn mock()
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
            )
        } doReturn membershipPackage
    }
    private val config = mock<SmartConfig>()
    private val memberTypeChecker = mock<MemberTypeChecker> {
        on { isMgm(member.toAvro()) } doReturn false
        on { getMgmMemberInfo(owner) } doReturn mgm
    }

    private val handler = ApproveRegistrationHandler(
        membershipPersistenceClient,
        membershipQueryClient,
        cipherSchemeMetadata,
        clock,
        cryptoOpsClient,
        cordaAvroSerializationFactory,
        merkleTreeProvider,
        memberTypeChecker,
        config,
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
    fun `invoke uses the correct TTL configuration`() {
        handler.invoke(state, key, command)

        verify(config, atLeastOnce()).getIsNull("$TTLS.$MEMBERS_PACKAGE_UPDATE")
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

        handler.invoke(state, key, command)

        verify(membershipPersistenceClient).addNotaryToGroupParameters(
            viewOwningIdentity = mgm.holdingIdentity,
            notary = notaryInfo,
        )
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

    private fun mockMemberInfo(
        holdingIdentity: HoldingIdentity,
        isMgm: Boolean = false,
        status: String = MemberInfoExtension.MEMBER_STATUS_ACTIVE,
        isNotary: Boolean = false,
    ): MemberInfo {
        val mgmContext = mock<MGMContext> {
            on { parseOrNull(eq(IS_MGM), any<Class<Boolean>>()) } doReturn isMgm
            on { parse(eq(STATUS), any<Class<String>>()) } doReturn status
            on { entries } doReturn mapOf("mgm" to holdingIdentity.x500Name.toString()).entries
        }
        val memberContext = mock<MemberContext> {
            on { parse(eq(MemberInfoExtension.GROUP_ID), any<Class<String>>()) } doReturn holdingIdentity.groupId
            if (isNotary) {
                on { entries } doReturn mapOf(
                    "member" to holdingIdentity.x500Name.toString(),
                    "$ROLES_PREFIX.0" to "notary",
                ).entries
                val notaryDetails = MemberNotaryDetails(
                    holdingIdentity.x500Name,
                    "Notary Plugin A",
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
        }
    }

    private fun createHoldingIdentity(name: String): HoldingIdentity {
        return createTestHoldingIdentity("C=GB,L=London,O=$name", GROUP_ID)
    }
}
