package net.corda.membership.impl.registration.dynamic.mgm.handler

import net.corda.crypto.client.CryptoOpsClient
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.membership.command.registration.ApproveRegistration
import net.corda.data.membership.p2p.DistributionType
import net.corda.data.membership.p2p.MembershipPackage
import net.corda.data.membership.state.RegistrationState
import net.corda.layeredpropertymap.toAvro
import net.corda.membership.impl.registration.dynamic.mgm.handler.helpers.MembershipPackageFactory
import net.corda.membership.impl.registration.dynamic.mgm.handler.helpers.P2pRecordsFactory
import net.corda.membership.impl.registration.dynamic.mgm.handler.helpers.SignerFactory
import net.corda.membership.lib.impl.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.lib.impl.MemberInfoExtension.Companion.holdingIdentity
import net.corda.membership.lib.impl.MemberInfoExtension.Companion.isMgm
import net.corda.membership.lib.impl.MemberInfoExtension.Companion.status
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Membership.Companion.MEMBER_LIST_TOPIC
import net.corda.utilities.time.Clock
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.crypto.DigestService
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toAvro
import net.corda.virtualnode.toCorda
import java.util.UUID

@Suppress("LongParameterList")
internal class ApproveRegistrationHandler(
    private val membershipPersistenceClient: MembershipPersistenceClient,
    private val membershipQueryClient: MembershipQueryClient,
    cipherSchemeMetadata: CipherSchemeMetadata,
    hashingService: DigestService,
    clock: Clock,
    cryptoOpsClient: CryptoOpsClient,
    cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    private val signerFactory: SignerFactory = SignerFactory(cryptoOpsClient),
    private val p2pRecordsFactory: P2pRecordsFactory = P2pRecordsFactory(
        cordaAvroSerializationFactory,
        clock,
    ),
    private val membershipPackageFactory: MembershipPackageFactory = MembershipPackageFactory(
        clock,
        hashingService,
        cordaAvroSerializationFactory,
        cipherSchemeMetadata,
        DistributionType.STANDARD,
    ) { UUID.randomUUID().toString() }
) : RegistrationHandler<ApproveRegistration> {

    override val commandType = ApproveRegistration::class.java
    override fun invoke(key: String, command: ApproveRegistration): RegistrationHandlerResult {
        // Update the state of the request and member
        val approvedBy = command.approvedBy.toCorda()
        val approvedMember = command.approvedMember.toCorda()
        val persistState = membershipPersistenceClient.setMemberAndRegistrationRequestAsApproved(
            viewOwningIdentity = approvedBy,
            approvedMember = approvedMember,
            registrationRequestId = command.registrationId,
        )
        val memberInfo = persistState.getOrThrow()

        val allMembers = getAllMembers(approvedBy).filter {
            it.status == MEMBER_STATUS_ACTIVE
        }
        val membershipPackageFactory = createMembershipPackageFactory(
            command.approvedBy.toCorda(),
            allMembers,
        )

        // Push member to member list kafka topic
        val persistentMemberInfo = PersistentMemberInfo.newBuilder()
            .setMemberContext(memberInfo.memberProvidedContext.toAvro())
            .setViewOwningMember(command.approvedBy)
            .setMgmContext(memberInfo.mgmProvidedContext.toAvro())
            .build()
        val memberRecord = Record(
            topic = MEMBER_LIST_TOPIC,
            key = "${approvedBy.id}-${approvedMember.id}",
            value = persistentMemberInfo,
        )

        // Send all approved members from the same group to the newly approved member over P2P
        val allMembersPackage = membershipPackageFactory.invoke(allMembers)
        val allMembersToNewMember = p2pRecordsFactory.createAuthenticatedMessageRecord(
            source = command.approvedBy,
            destination = command.approvedMember,
            content = allMembersPackage,
        )

        // Send the newly approved member to all other members in the same group over P2P
        val memberPackage = membershipPackageFactory.invoke(listOf(memberInfo))
        val memberToAllMembers = allMembers.filter {
            it.holdingIdentity != command.approvedMember.toCorda()
        }.map { memberToSendUpdateTo ->
            p2pRecordsFactory.createAuthenticatedMessageRecord(
                source = command.approvedBy,
                destination = memberToSendUpdateTo.holdingIdentity.toAvro(),
                content = memberPackage,
            )
        }

        return RegistrationHandlerResult(
            RegistrationState(command.registrationId, command.approvedMember),
            memberToAllMembers + memberRecord + allMembersToNewMember
        )
    }

    private fun createMembershipPackageFactory(
        owner: HoldingIdentity,
        members: Collection<MemberInfo>
    ): (Collection<MemberInfo>) -> MembershipPackage {
        val mgm = members.firstOrNull {
            it.isMgm
        } ?: throw FailToFindMgm
        val mgmSigner = signerFactory.createSigner(mgm)
        val signatures = membershipQueryClient
            .queryMembersSignatures(
                owner,
                members.map {
                    it.holdingIdentity
                }
            ).getOrThrow()
        return {
            membershipPackageFactory.createMembershipPackage(
                mgmSigner,
                signatures,
                it,
            )
        }
    }

    private fun getAllMembers(owner: HoldingIdentity): Collection<MemberInfo> {
        return membershipQueryClient.queryMemberInfo(owner).getOrThrow()
    }
    internal object FailToFindMgm : CordaRuntimeException("Could not find MGM")
}
