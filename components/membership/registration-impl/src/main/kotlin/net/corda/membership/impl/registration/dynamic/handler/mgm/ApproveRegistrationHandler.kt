package net.corda.membership.impl.registration.dynamic.handler.mgm

import net.corda.crypto.client.CryptoOpsClient
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.data.membership.command.registration.mgm.ApproveRegistration
import net.corda.data.membership.command.registration.mgm.DeclineRegistration
import net.corda.data.membership.common.RegistrationStatus
import net.corda.data.membership.p2p.DistributionType
import net.corda.data.membership.p2p.MembershipPackage
import net.corda.data.membership.p2p.SetOwnRegistrationStatus
import net.corda.data.membership.state.RegistrationState
import net.corda.layeredpropertymap.toAvro
import net.corda.libs.configuration.SmartConfig
import net.corda.membership.impl.registration.dynamic.handler.MemberTypeChecker
import net.corda.membership.impl.registration.dynamic.handler.MissingRegistrationStateException
import net.corda.membership.impl.registration.dynamic.handler.RegistrationHandler
import net.corda.membership.impl.registration.dynamic.handler.RegistrationHandlerResult
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.lib.MemberInfoExtension.Companion.holdingIdentity
import net.corda.membership.lib.MemberInfoExtension.Companion.isMgm
import net.corda.membership.lib.MemberInfoExtension.Companion.notaryDetails
import net.corda.membership.lib.MemberInfoExtension.Companion.status
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import net.corda.membership.p2p.helpers.MembershipPackageFactory
import net.corda.membership.p2p.helpers.MerkleTreeGenerator
import net.corda.membership.p2p.helpers.P2pRecordsFactory
import net.corda.membership.p2p.helpers.P2pRecordsFactory.Companion.getTtlMinutes
import net.corda.membership.p2p.helpers.SignerFactory
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipPersistenceResult
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Membership.Companion.MEMBER_LIST_TOPIC
import net.corda.schema.Schemas.Membership.Companion.REGISTRATION_COMMAND_TOPIC
import net.corda.schema.configuration.MembershipConfig.TtlsConfig.MEMBERS_PACKAGE_UPDATE
import net.corda.utilities.time.Clock
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.merkle.MerkleTreeProvider
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
    clock: Clock,
    cryptoOpsClient: CryptoOpsClient,
    cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    merkleTreeProvider: MerkleTreeProvider,
    private val memberTypeChecker: MemberTypeChecker,
    private val membershipConfig: SmartConfig,
    private val signerFactory: SignerFactory = SignerFactory(cryptoOpsClient),
    private val merkleTreeGenerator: MerkleTreeGenerator = MerkleTreeGenerator(
        merkleTreeProvider,
        cordaAvroSerializationFactory
    ),
    private val p2pRecordsFactory: P2pRecordsFactory = P2pRecordsFactory(
        cordaAvroSerializationFactory,
        clock,
    ),
    private val membershipPackageFactory: MembershipPackageFactory = MembershipPackageFactory(
        clock,
        cordaAvroSerializationFactory,
        cipherSchemeMetadata,
        DistributionType.STANDARD,
        merkleTreeGenerator,
    ) { UUID.randomUUID().toString() }
) : RegistrationHandler<ApproveRegistration> {
    private companion object {
        val logger = contextLogger()
    }

    override val commandType = ApproveRegistration::class.java

    override fun invoke(state: RegistrationState?, key: String, command: ApproveRegistration): RegistrationHandlerResult {
        if (state == null) throw MissingRegistrationStateException
        // Update the state of the request and member
        val approvedBy = state.mgm
        val approvedMember = state.registeringMember
        val registrationId = state.registrationId
        val messages = try {
            val mgm = memberTypeChecker.getMgmMemberInfo(approvedBy.toCorda())
                ?: throw CordaRuntimeException(
                    "Could not approve registration request: '$registrationId' - member ${approvedBy.x500Name} is not an MGM."
                )
            if (memberTypeChecker.isMgm(approvedMember)) {
                throw CordaRuntimeException(
                    "The registration request: '$registrationId' cannot be approved by ${approvedMember.x500Name} as it is an MGM."
                )
            }

            val persistState = membershipPersistenceClient.setMemberAndRegistrationRequestAsApproved(
                viewOwningIdentity = approvedBy.toCorda(),
                approvedMember = approvedMember.toCorda(),
                registrationRequestId = registrationId,
            )
            val memberInfo = persistState.getOrThrow()

            val allMembers = getAllMembers(approvedBy.toCorda())
            val members = allMembers.filter {
                it.status == MEMBER_STATUS_ACTIVE && !it.isMgm
            }
            val membershipPackageFactory = createMembershipPackageFactory(mgm, members)

            // Push member to member list kafka topic
            val persistentMemberInfo = PersistentMemberInfo.newBuilder()
                .setMemberContext(memberInfo.memberProvidedContext.toAvro())
                .setViewOwningMember(approvedBy)
                .setMgmContext(memberInfo.mgmProvidedContext.toAvro())
                .build()
            val memberRecord = Record(
                topic = MEMBER_LIST_TOPIC,
                key = "${approvedBy.toCorda().shortHash}-${approvedMember.toCorda().shortHash}",
                value = persistentMemberInfo,
            )

            // Send all approved members from the same group to the newly approved member over P2P
            val allMembersPackage = membershipPackageFactory.invoke(members)
            val allMembersToNewMember = p2pRecordsFactory.createAuthenticatedMessageRecord(
                source = approvedBy,
                destination = approvedMember,
                content = allMembersPackage,
            )

            // Send the newly approved member to all other members in the same group over P2P
            val memberPackage = membershipPackageFactory.invoke(listOf(memberInfo))
            val memberToAllMembers = members.filter {
                it.holdingIdentity != approvedMember.toCorda()
            }.map { memberToSendUpdateTo ->
                p2pRecordsFactory.createAuthenticatedMessageRecord(
                    source = approvedBy,
                    destination = memberToSendUpdateTo.holdingIdentity.toAvro(),
                    content = memberPackage,
                    minutesToWait = membershipConfig.getTtlMinutes(MEMBERS_PACKAGE_UPDATE),
                )
            }

            val persistApproveMessage = p2pRecordsFactory.createAuthenticatedMessageRecord(
                source = approvedBy,
                destination = approvedMember,
                content = SetOwnRegistrationStatus(
                    registrationId,
                    RegistrationStatus.APPROVED
                )
            )

            // If approved member has notary role set, add notary to MGM's view of the group parameters.
            memberInfo.notaryDetails
                ?.let { membershipPersistenceClient.addNotaryToGroupParameters(mgm.holdingIdentity, memberInfo) }
                ?.apply {
                    if (this is MembershipPersistenceResult.Failure) {
                        throw MembershipPersistenceException("Failed to update group parameters with notary information of" +
                            " '${memberInfo.name}', which has role set to 'notary'.")
                    }
                }

            memberToAllMembers + memberRecord + allMembersToNewMember + persistApproveMessage
        } catch (e: Exception) {
            logger.warn("Could not approve registration request: '$registrationId'", e)
            listOf(
                Record(
                    REGISTRATION_COMMAND_TOPIC,
                    key,
                    RegistrationCommand(
                        DeclineRegistration(e.message)
                    )
                ),
            )
        }

        return RegistrationHandlerResult(
            RegistrationState(registrationId, approvedMember, approvedBy),
            messages
        )
    }

    private fun createMembershipPackageFactory(
        mgm: MemberInfo,
        members: Collection<MemberInfo>
    ): (Collection<MemberInfo>) -> MembershipPackage {
        val mgmSigner = signerFactory.createSigner(mgm)
        val signatures = membershipQueryClient
            .queryMembersSignatures(
                mgm.holdingIdentity,
                members.map {
                    it.holdingIdentity
                }
            ).getOrThrow()
        val membersTree = merkleTreeGenerator.generateTree(members)

        return {
            membershipPackageFactory.createMembershipPackage(
                mgmSigner,
                signatures,
                it,
                membersTree.root,
            )
        }
    }

    private fun getAllMembers(owner: HoldingIdentity): Collection<MemberInfo> {
        return membershipQueryClient.queryMemberInfo(owner).getOrThrow()
    }
}
