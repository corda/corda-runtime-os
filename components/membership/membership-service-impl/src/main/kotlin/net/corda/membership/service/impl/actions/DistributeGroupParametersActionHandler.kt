package net.corda.membership.service.impl.actions

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.cipher.suite.merkle.MerkleTreeProvider
import net.corda.crypto.client.CryptoOpsClient
import net.corda.data.membership.actions.request.DistributeGroupParameters
import net.corda.data.membership.actions.request.MembershipActionsRequest
import net.corda.data.membership.p2p.DistributionType
import net.corda.data.membership.p2p.MembershipPackage
import net.corda.libs.configuration.SmartConfig
import net.corda.membership.lib.InternalGroupParameters
import net.corda.membership.lib.MemberInfoExtension.Companion.holdingIdentity
import net.corda.membership.lib.MemberInfoExtension.Companion.isMgm
import net.corda.membership.locally.hosted.identities.LocallyHostedIdentitiesService
import net.corda.membership.p2p.helpers.MembershipPackageFactory
import net.corda.membership.p2p.helpers.MerkleTreeGenerator
import net.corda.membership.p2p.helpers.P2pRecordsFactory
import net.corda.membership.p2p.helpers.P2pRecordsFactory.Companion.getTtlMinutes
import net.corda.membership.p2p.helpers.SignerFactory
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.records.Record
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
class DistributeGroupParametersActionHandler(
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
    private val p2pRecordsFactory: P2pRecordsFactory = P2pRecordsFactory(
        cordaAvroSerializationFactory,
        clock,
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

    fun process(key: String, request: DistributeGroupParameters): List<Record<String, *>> {
        val approvedBy = request.mgm

        val groupReader = groupReaderProvider.getGroupReader(approvedBy.toCorda())

        // Verify that the group parameters from the reader are the newly updated ones.
        // If not, republish the distribute command to be processed later when the updated set of group parameters
        // is available.
        val groupParameters = groupReader.groupParameters ?: return recordToRequeueDistribution(key, request) {
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

        val allActiveMembers = groupReader.lookup()
        val allActiveMembersExcludingMgm = allActiveMembers.filterNot { it.isMgm }

        val membershipPackageFactory = createMembershipPackageFactory(allActiveMembers.first { it.isMgm })

        val memberPackage = try {
            membershipPackageFactory(groupParameters)
        } catch (except: CordaRuntimeException) {
            return recordToRequeueDistribution(key, request) {
                logger.warn(
                    "Failed to create membership package for distribution of group parameters to the rest of " +
                        "the group. Distribution will be reattempted.",
                    except
                )
            }
        }

        val groupParametersToAllActiveMembers = allActiveMembersExcludingMgm.map { memberToSendUpdateTo ->
            p2pRecordsFactory.createAuthenticatedMessageRecord(
                source = approvedBy,
                destination = memberToSendUpdateTo.holdingIdentity.toAvro(),
                content = memberPackage,
                minutesToWait = membershipConfig.getTtlMinutes(MembershipConfig.TtlsConfig.MEMBERS_PACKAGE_UPDATE),
            )
        }

        return groupParametersToAllActiveMembers
    }

    private fun recordToRequeueDistribution(
        key: String,
        request: DistributeGroupParameters,
        logMessage: () -> Unit
    ): List<Record<String, *>> {
        logMessage()
        return listOf(Record(Schemas.Membership.MEMBERSHIP_ACTIONS_TOPIC, key, MembershipActionsRequest(request)))
    }

    private fun createMembershipPackageFactory(
        mgm: MemberInfo,
    ): (InternalGroupParameters) -> MembershipPackage {
        val mgmSigner = signerFactory.createSigner(mgm)

        return { groupParameters ->
            membershipPackageFactory.createGroupParametersPackage(
                mgmSigner,
                groupParameters,
            )
        }
    }
}
