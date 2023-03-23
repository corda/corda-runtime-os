package net.corda.membership.service.impl.actions

import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.cipher.suite.merkle.MerkleTreeProvider
import net.corda.crypto.client.CryptoOpsClient
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.membership.actions.request.DistributeMemberInfo
import net.corda.data.membership.actions.request.MembershipActionsRequest
import net.corda.data.membership.p2p.DistributionType
import net.corda.data.membership.p2p.MembershipPackage
import net.corda.libs.configuration.SmartConfig
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
import net.corda.membership.persistence.client.MembershipQueryResult
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import net.corda.schema.configuration.MembershipConfig
import net.corda.utilities.time.Clock
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.membership.GroupParameters
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toAvro
import net.corda.virtualnode.toCorda
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.UUID

@Suppress("LongParameterList")
class DistributeMemberInfoAction(
    private val membershipQueryClient: MembershipQueryClient,
    cipherSchemeMetadata: CipherSchemeMetadata,
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
)  {
    private companion object {
        val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    fun process(key: String, request: DistributeMemberInfo): List<Record<String, *>> {
        val approvedBy = request.mgm
        val updatedMember = request.updatedMember

        val groupReader = groupReaderProvider.getGroupReader(approvedBy.toCorda())

        // Verify that the group parameters from the reader are the ones persisted during registration approval.
        // If not, republish the distribute command to be processed later when the updated set of group parameters
        // is available.
        val groupParameters = groupReader.groupParameters?.apply {
            if (request.minimumGroupParametersEpoch > epoch) {
                return recordToRequeueDistribution(key, request) {
                    logger.info("Retrieved group parameters are outdated (current epoch $epoch). Republishing the distribute command to " +
                            "be processed later when the set of group parameters with epoch ${request.minimumGroupParametersEpoch} is " +
                            "available.")
                }
            }
        } ?: return recordToRequeueDistribution(key, request) {
            logger.info("Retrieved group parameters are null. Republishing the distribute command to be processed later when set of " +
                    "group parameters with epoch ${request.minimumGroupParametersEpoch} is available.")
        }

        val allMembers = groupReader.lookup()
        val allActiveMembersExcludingMgm = allMembers.filter {
            it.status == MemberInfoExtension.MEMBER_STATUS_ACTIVE && !it.isMgm
        }
        val updatedMemberInfo = allActiveMembersExcludingMgm.first { it.name.toString() == updatedMember.x500Name }
        val mgm = allMembers.first { it.isMgm }

        val membersSignaturesQuery = membershipQueryClient.queryMembersSignatures(
            mgm.holdingIdentity,
            allActiveMembersExcludingMgm.map { it.holdingIdentity }
        )
        val membersSignatures = when (membersSignaturesQuery) {
            is MembershipQueryResult.Success -> membersSignaturesQuery.payload
            is MembershipQueryResult.Failure -> return recordToRequeueDistribution(key, request) {
                logger.warn("Failed to query for the members signature: ${membersSignaturesQuery.errorMsg}. Distributing the member info " +
                        "will be reattempted.")
            }
        }
        val membershipPackageFactory = createMembershipPackageFactory(mgm, allActiveMembersExcludingMgm, membersSignatures)

        // Send all approved members from the same group to the newly approved member over P2P
        val allMembersExcludingMgmPackage = try {
            membershipPackageFactory.invoke(allActiveMembersExcludingMgm, groupParameters)
        } catch (except: CordaRuntimeException) {
            return recordToRequeueDistribution(key, request) {
                logger.warn("Failed to create membership package for distribution to $updatedMember. Distributing the member info will " +
                        "be reattempted.", except)
            }
        }

        val allMembersToUpdatedMember = p2pRecordsFactory.createAuthenticatedMessageRecord(
            source = approvedBy,
            destination = updatedMember,
            content = allMembersExcludingMgmPackage,
        )

        // Send the newly approved member to all other members in the same group over P2P
        val memberPackage = try {
            membershipPackageFactory.invoke(listOf(updatedMemberInfo), groupParameters)
        } catch (except: CordaRuntimeException) {
            return recordToRequeueDistribution(key, request) {
                logger.warn("Failed to create membership package for distribution of the $updatedMember to the rest of the group. " +
                        "Distributing the member info will be reattempted.", except)
            }
        }
        val updatedMemberToAllMembers = allActiveMembersExcludingMgm.filter {
            it.holdingIdentity != updatedMember.toCorda()
        }.map { memberToSendUpdateTo ->
            p2pRecordsFactory.createAuthenticatedMessageRecord(
                source = approvedBy,
                destination = memberToSendUpdateTo.holdingIdentity.toAvro(),
                content = memberPackage,
                minutesToWait = membershipConfig.getTtlMinutes(MembershipConfig.TtlsConfig.MEMBERS_PACKAGE_UPDATE),
            )
        }

        return updatedMemberToAllMembers + allMembersToUpdatedMember
    }

    private fun recordToRequeueDistribution(
        key: String,
        request: DistributeMemberInfo,
        logMessage: () -> Unit
    ): List<Record<String, *>> {
        logMessage()
        return listOf(Record(Schemas.Membership.MEMBERSHIP_ACTIONS_TOPIC, key, MembershipActionsRequest(request)))
    }

    private fun createMembershipPackageFactory(
        mgm: MemberInfo,
        members: Collection<MemberInfo>,
        membersSignatures: Map<HoldingIdentity, Pair<CryptoSignatureWithKey, CryptoSignatureSpec>>,
    ): (Collection<MemberInfo>, GroupParameters) -> MembershipPackage {
        val mgmSigner = signerFactory.createSigner(mgm)
        val membersTree = merkleTreeGenerator.generateTree(members)

        return { membersToSend, groupParameters ->
            membershipPackageFactory.createMembershipPackage(
                mgmSigner,
                membersSignatures,
                membersToSend,
                membersTree.root,
                groupParameters,
            )
        }
    }
}