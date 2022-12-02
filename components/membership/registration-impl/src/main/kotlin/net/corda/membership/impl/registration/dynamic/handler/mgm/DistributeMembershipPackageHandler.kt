package net.corda.membership.impl.registration.dynamic.handler.mgm

import net.corda.crypto.client.CryptoOpsClient
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.data.membership.command.registration.mgm.DistributeMembershipPackage
import net.corda.data.membership.p2p.DistributionType
import net.corda.data.membership.p2p.MembershipPackage
import net.corda.data.membership.state.RegistrationState
import net.corda.libs.configuration.SmartConfig
import net.corda.membership.impl.registration.dynamic.handler.MissingRegistrationStateException
import net.corda.membership.impl.registration.dynamic.handler.RegistrationHandler
import net.corda.membership.impl.registration.dynamic.handler.RegistrationHandlerResult
import net.corda.membership.lib.MemberInfoExtension
import net.corda.membership.lib.MemberInfoExtension.Companion.holdingIdentity
import net.corda.membership.lib.MemberInfoExtension.Companion.isMgm
import net.corda.membership.lib.MemberInfoExtension.Companion.status
import net.corda.membership.p2p.helpers.MembershipPackageFactory
import net.corda.membership.p2p.helpers.MerkleTreeGenerator
import net.corda.membership.p2p.helpers.P2pRecordsFactory
import net.corda.membership.p2p.helpers.P2pRecordsFactory.Companion.getTtlMinutes
import net.corda.membership.p2p.helpers.SignerFactory
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Membership.Companion.REGISTRATION_COMMAND_TOPIC
import net.corda.schema.configuration.MembershipConfig
import net.corda.utilities.time.Clock
import net.corda.v5.base.util.contextLogger
import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.merkle.MerkleTreeProvider
import net.corda.v5.membership.GroupParameters
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.toAvro
import net.corda.virtualnode.toCorda
import java.util.UUID

@Suppress("LongParameterList")
class DistributeMembershipPackageHandler(
    private val membershipQueryClient: MembershipQueryClient,
    cipherSchemeMetadata: _root_ide_package_.net.corda.crypto.cipher.suite.CipherSchemeMetadata,
    clock: Clock,
    cryptoOpsClient: CryptoOpsClient,
    cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    merkleTreeProvider: MerkleTreeProvider,
    private val membershipConfig: SmartConfig,
    private val groupReaderProvider: MembershipGroupReaderProvider,
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
) : RegistrationHandler<DistributeMembershipPackage> {
    private companion object {
        val logger = contextLogger()
    }

    override val commandType = DistributeMembershipPackage::class.java

    override fun invoke(
        state: RegistrationState?,
        key: String,
        command: DistributeMembershipPackage
    ): RegistrationHandlerResult {
        if (state == null) throw MissingRegistrationStateException
        val approvedBy = state.mgm
        val approvedMember = state.registeringMember
        val registrationId = state.registrationId

        val groupReader = groupReaderProvider.getGroupReader(approvedBy.toCorda())

        // Verify that the group parameters from the reader are the ones persisted during registration approval.
        // If not, republish the distribute command to be processed later when the updated set of group parameters
        // is available.
        val groupParameters = groupReader.groupParameters?.apply {
            if (epoch != command.groupParametersEpoch) {
                return logAndReattemptDistribution(state, key, command)
            }
        } ?: return logAndReattemptDistribution(state, key, command)

        val messages = try {
            val allMembers = groupReader.lookup()
            val members = allMembers.filter {
                it.status == MemberInfoExtension.MEMBER_STATUS_ACTIVE && !it.isMgm
            }
            val approvedMemberInfo = members.first { it.name.toString() == approvedMember.x500Name }
            val mgm = allMembers.first { it.isMgm }

            val membershipPackageFactory = createMembershipPackageFactory(mgm, members)

            // Send all approved members from the same group to the newly approved member over P2P
            val allMembersPackage = membershipPackageFactory.invoke(members, groupParameters)
            val allMembersToNewMember = p2pRecordsFactory.createAuthenticatedMessageRecord(
                source = approvedBy,
                destination = approvedMember,
                content = allMembersPackage,
            )

            // Send the newly approved member to all other members in the same group over P2P
            val memberPackage = membershipPackageFactory.invoke(listOf(approvedMemberInfo), groupParameters)
            val memberToAllMembers = members.filter {
                it.holdingIdentity != approvedMember.toCorda()
            }.map { memberToSendUpdateTo ->
                p2pRecordsFactory.createAuthenticatedMessageRecord(
                    source = approvedBy,
                    destination = memberToSendUpdateTo.holdingIdentity.toAvro(),
                    content = memberPackage,
                    minutesToWait = membershipConfig.getTtlMinutes(MembershipConfig.TtlsConfig.MEMBERS_PACKAGE_UPDATE),
                )
            }

            memberToAllMembers + allMembersToNewMember
        } catch (e: Exception) {
            logger.warn("Could not distribute membership packages after registration request: '$registrationId' was approved. " +
                        "Distribution will be reattempted.", e)
            listOf(Record(REGISTRATION_COMMAND_TOPIC, key, RegistrationCommand(command)))
        }

        return RegistrationHandlerResult(
            RegistrationState(registrationId, approvedMember, approvedBy),
            messages
        )
    }

    private fun logAndReattemptDistribution(
        state: RegistrationState?,
        key: String,
        command: DistributeMembershipPackage
    ): RegistrationHandlerResult {
        logger.info("Retrieved group parameters are outdated or null. Republishing the distribute command to be processed" +
                " later when the updated set of group parameters is available.")
        return RegistrationHandlerResult(state, listOf(Record(REGISTRATION_COMMAND_TOPIC, key, RegistrationCommand(command))))
    }

    private fun createMembershipPackageFactory(
        mgm: MemberInfo,
        members: Collection<MemberInfo>
    ): (Collection<MemberInfo>, GroupParameters) -> MembershipPackage {
        val mgmSigner = signerFactory.createSigner(mgm)
        val signatures = membershipQueryClient
            .queryMembersSignatures(
                mgm.holdingIdentity,
                members.map {
                    it.holdingIdentity
                }
            ).getOrThrow()
        val membersTree = merkleTreeGenerator.generateTree(members)

        return { membersToSend, groupParameters ->
            membershipPackageFactory.createMembershipPackage(
                mgmSigner,
                signatures,
                membersToSend,
                membersTree.root,
                groupParameters,
            )
        }
    }
}
