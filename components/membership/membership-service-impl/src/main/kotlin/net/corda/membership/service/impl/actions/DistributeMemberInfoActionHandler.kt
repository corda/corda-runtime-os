package net.corda.membership.service.impl.actions

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.cipher.suite.merkle.MerkleTreeProvider
import net.corda.crypto.client.CryptoOpsClient
import net.corda.data.membership.actions.request.DistributeMemberInfo
import net.corda.data.membership.actions.request.MembershipActionsRequest
import net.corda.data.membership.p2p.DistributionType
import net.corda.data.membership.p2p.MembershipPackage
import net.corda.data.p2p.app.MembershipStatusFilter.ACTIVE_OR_SUSPENDED
import net.corda.libs.configuration.SmartConfig
import net.corda.membership.lib.InternalGroupParameters
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_SUSPENDED
import net.corda.membership.lib.MemberInfoExtension.Companion.holdingIdentity
import net.corda.membership.lib.MemberInfoExtension.Companion.isMgm
import net.corda.membership.lib.MemberInfoExtension.Companion.status
import net.corda.membership.lib.SelfSignedMemberInfo
import net.corda.membership.locally.hosted.identities.LocallyHostedIdentitiesService
import net.corda.membership.p2p.helpers.MembershipPackageFactory
import net.corda.membership.p2p.helpers.MerkleTreeGenerator
import net.corda.membership.p2p.helpers.SignerFactory
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.persistence.client.MembershipQueryResult
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.records.Record
import net.corda.p2p.messaging.P2pRecordsFactory
import net.corda.p2p.messaging.P2pRecordsFactory.Companion.MEMBERSHIP_DATA_DISTRIBUTION_PREFIX
import net.corda.p2p.messaging.P2pRecordsFactory.Companion.getTtlMinutes
import net.corda.schema.Schemas
import net.corda.schema.configuration.MembershipConfig
import net.corda.utilities.time.Clock
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.toAvro
import net.corda.virtualnode.toCorda
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.UUID

@Suppress("LongParameterList")
class DistributeMemberInfoActionHandler(
    private val membershipQueryClient: MembershipQueryClient,
    cipherSchemeMetadata: CipherSchemeMetadata,
    clock: Clock,
    cryptoOpsClient: CryptoOpsClient,
    cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    merkleTreeProvider: MerkleTreeProvider,
    private val membershipConfig: SmartConfig,
    private val groupReaderProvider: MembershipGroupReaderProvider,
    locallyHostedIdentitiesService: LocallyHostedIdentitiesService,
    private val signerFactory: SignerFactory = SignerFactory(cryptoOpsClient, locallyHostedIdentitiesService),
    private val merkleTreeGenerator: MerkleTreeGenerator = MerkleTreeGenerator(
        merkleTreeProvider,
        cordaAvroSerializationFactory
    ),
    private val membershipP2PRecordsFactory: P2pRecordsFactory = P2pRecordsFactory(
        clock,
        cordaAvroSerializationFactory,
    ),
    private val membershipPackageFactory: MembershipPackageFactory = MembershipPackageFactory(
        clock,
        cipherSchemeMetadata,
        DistributionType.STANDARD,
        merkleTreeGenerator,
    ) { UUID.randomUUID().toString() }
) {
    private companion object {
        val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    fun process(key: String, request: DistributeMemberInfo): List<Record<String, *>> {
        val approvedBy = request.mgm
        val updatedMember = request.updatedMember

        val groupReader = groupReaderProvider.getGroupReader(approvedBy.toCorda())
        val updatedMemberQuery = membershipQueryClient
            .queryMemberInfo(
                approvedBy.toCorda(),
                listOf(updatedMember.toCorda()),
                listOf(MEMBER_STATUS_ACTIVE, MEMBER_STATUS_SUSPENDED)
            )
        val updatedMemberInfo = when (updatedMemberQuery) {
            is MembershipQueryResult.Success -> updatedMemberQuery.payload
            is MembershipQueryResult.Failure -> return recordToRequeueDistribution(key, request) {
                logger.warn(
                    "Failed to query for updated member's info: ${updatedMemberQuery.errorMsg}." +
                        "Distributing the member info will be reattempted."
                )
            }
        }.firstOrNull() ?: return recordToRequeueDistribution(key, request) {
            logger.info(
                "Could not retrieve MemberInfo from the database for ${updatedMember.x500Name}. " +
                    "Republishing the distribute command to be processed later."
            )
        }

        request.minimumUpdatedMemberSerial?.let {
            if (request.minimumUpdatedMemberSerial > updatedMemberInfo.serial) {
                return recordToRequeueDistribution(key, request) {
                    logger.info(
                        "The MemberInfo retrieved from the database for ${updatedMember.x500Name} has serial" +
                            " ${updatedMemberInfo.serial}, which is an old version. Republishing the distribute command to be processed " +
                            "later when the MemberInfo with serial ${request.minimumUpdatedMemberSerial} is available."
                    )
                }
            }
        }
        // Verify that the group parameters from the reader are the ones persisted during registration approval.
        // If not, republish the distribute command to be processed later when the updated set of group parameters
        // is available.
        val groupParameters = groupReader.groupParameters?.apply {
        } ?: return recordToRequeueDistribution(key, request) {
            logger.info(
                "Retrieved group parameters are null. Republishing the distribute command to be processed later when set of " +
                    "group parameters with epoch ${request.minimumGroupParametersEpoch} is available."
            )
        }
        request.minimumGroupParametersEpoch?.let {
            if (it > groupParameters.epoch) {
                return recordToRequeueDistribution(key, request) {
                    logger.info(
                        "Retrieved group parameters are outdated (current epoch ${groupParameters.epoch}). Republishing the " +
                            "distribute command to be processed later when the set of group parameters with epoch " +
                            "${request.minimumGroupParametersEpoch} is available."
                    )
                }
            }
        }

        val allNonPendingMembersQuery = membershipQueryClient
            .queryMemberInfo(
                approvedBy.toCorda(),
                listOf(MEMBER_STATUS_ACTIVE, MEMBER_STATUS_SUSPENDED)
            )
        val allNonPendingMemberInfo = when (allNonPendingMembersQuery) {
            is MembershipQueryResult.Success -> allNonPendingMembersQuery.payload
            is MembershipQueryResult.Failure -> return recordToRequeueDistribution(key, request) {
                logger.warn(
                    "Failed to query for membership group's info: ${allNonPendingMembersQuery.errorMsg}." +
                        "Distributing the member info will be reattempted."
                )
            }
        }
        val allNonPendingMembersExcludingMgm = allNonPendingMemberInfo.filterNot { it.isMgm }
        // If the updated member is suspended then we only send its own member info to itself (so it can tell it has been suspended).
        val membersToDistributeToUpdatedMember = if (updatedMemberInfo.status == MEMBER_STATUS_SUSPENDED) {
            listOf(updatedMemberInfo)
        } else {
            allNonPendingMembersExcludingMgm
        }

        val mgm = allNonPendingMemberInfo.first { it.isMgm }
        val membershipPackageFactory = createMembershipPackageFactory(mgm, membersToDistributeToUpdatedMember)

        // Send all non-pending members from the same group to the newly approved member over P2P
        val membersToDistributeToUpdatedMemberPackage = try {
            membershipPackageFactory(membersToDistributeToUpdatedMember, groupParameters)
        } catch (except: CordaRuntimeException) {
            return recordToRequeueDistribution(key, request) {
                logger.warn(
                    "Failed to create membership package for distribution to $updatedMember. Distributing the member info will " +
                        "be reattempted.",
                    except
                )
            }
        }

        val allMembersToUpdatedMember = membershipP2PRecordsFactory.createMembershipAuthenticatedMessageRecord(
            source = approvedBy,
            destination = updatedMember,
            content = membersToDistributeToUpdatedMemberPackage,
            messageIdPrefix = MEMBERSHIP_DATA_DISTRIBUTION_PREFIX,
            filter = ACTIVE_OR_SUSPENDED,
        )

        // Send the newly approved member to all other active members in the same group over P2P
        val memberPackage = try {
            membershipPackageFactory(listOf(updatedMemberInfo), groupParameters)
        } catch (except: CordaRuntimeException) {
            return recordToRequeueDistribution(key, request) {
                logger.warn(
                    "Failed to create membership package for distribution of the $updatedMember to the rest of the group. " +
                        "Distributing the member info will be reattempted.",
                    except
                )
            }
        }

        val updatedMemberToAllMembers = allNonPendingMembersExcludingMgm.filter {
            it.holdingIdentity != updatedMember.toCorda() && it.isActive
        }.map { memberToSendUpdateTo ->
            membershipP2PRecordsFactory.createMembershipAuthenticatedMessageRecord(
                source = approvedBy,
                destination = memberToSendUpdateTo.holdingIdentity.toAvro(),
                content = memberPackage,
                messageIdPrefix = MEMBERSHIP_DATA_DISTRIBUTION_PREFIX,
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
        members: Collection<SelfSignedMemberInfo>,
    ): (Collection<SelfSignedMemberInfo>, InternalGroupParameters) -> MembershipPackage {
        val mgmSigner = signerFactory.createSigner(mgm)
        val membersTree = merkleTreeGenerator.generateTreeUsingSignedMembers(members)

        return { membersToSend, groupParameters ->
            membershipPackageFactory.createMembershipPackage(
                mgmSigner,
                membersToSend,
                membersTree.root,
                groupParameters,
            )
        }
    }
}
