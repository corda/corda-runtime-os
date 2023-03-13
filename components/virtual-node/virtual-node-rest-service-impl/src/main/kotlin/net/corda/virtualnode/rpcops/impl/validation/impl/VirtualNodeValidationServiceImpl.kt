package net.corda.virtualnode.rpcops.impl.validation.impl

import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.crypto.core.ShortHash
import net.corda.crypto.core.ShortHashException
import net.corda.rest.exception.BadRequestException
import net.corda.rest.exception.InternalServerException
import net.corda.rest.exception.InvalidInputDataException
import net.corda.rest.exception.ResourceAlreadyExistsException
import net.corda.rest.exception.ResourceNotFoundException
import net.corda.libs.packaging.core.CpiMetadata
import net.corda.libs.virtualnode.endpoints.v1.types.CreateVirtualNodeRequest
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants
import net.corda.membership.lib.grouppolicy.GroupPolicyParseException
import net.corda.membership.lib.grouppolicy.GroupPolicyParser
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.OperationalStatus
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.rpcops.impl.validation.VirtualNodeValidationService
import java.util.UUID

internal class VirtualNodeValidationServiceImpl(
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
    private val cpiInfoReadService: CpiInfoReadService
) : VirtualNodeValidationService {

    private val validCpi = Regex("^[a-fA-F0-9]{12}")

    override fun validateVirtualNodeDoesNotExist(holdingIdentity: HoldingIdentity) {
        if (virtualNodeInfoReadService.getByHoldingIdentityShortHash(holdingIdentity.shortHash) != null) {
            throw ResourceAlreadyExistsException("Virtual Node", holdingIdentity.toString())
        }
    }

    override fun validateAndGetGroupId(request: CreateVirtualNodeRequest): String {
        try {
            MemberX500Name.parse(request.x500Name)
        } catch (e: Exception) {
            throw InvalidInputDataException(
                "X500 name \"${request.x500Name}\" could not be parsed. Cause: ${e.message}"
            )
        }

        val cpiFileChecksum = request.cpiFileChecksum

        if (!validCpi.matches(cpiFileChecksum)) {
            throw InvalidInputDataException(
                "CPI file checksum value '$cpiFileChecksum' is invalid, expecting 12 digit hex value"
            )
        }

        if (!request.vaultDdlConnection.isNullOrBlank() && request.vaultDmlConnection.isNullOrBlank()) {
            throw InvalidInputDataException(
                "If Vault DDL connection is provided, Vault DML connection needs to be provided as well."
            )
        }

        if (!request.cryptoDdlConnection.isNullOrBlank() && request.cryptoDmlConnection.isNullOrBlank()) {
            throw InvalidInputDataException(
                "If Crypto DDL connection is provided, Crypto DML connection needs to be provided as well."
            )
        }

        if (!request.uniquenessDdlConnection.isNullOrBlank() && request.uniquenessDmlConnection.isNullOrBlank()) {
            throw InvalidInputDataException(
                "If Uniqueness DDL connection is provided, Uniqueness DML connection needs to be provided as well."
            )
        }

        val cpiMeta = cpiInfoReadService.getAll()
            .firstOrNull { it.fileChecksum.toHexString().substring(0, 12) == cpiFileChecksum }
            ?: throw InvalidInputDataException(
                "No CPI metadata found for checksum '${cpiFileChecksum}'."
            )

        val groupPolicyJson = cpiMeta.groupPolicy
            ?: throw InternalServerException("Group policy is missing from CPI metadata '${cpiFileChecksum}'")

        val groupId = try {
            GroupPolicyParser.groupIdFromJson(groupPolicyJson)
        } catch (e: GroupPolicyParseException) {
            throw InternalServerException("Could not find group ID in CPI policy data '${groupPolicyJson}'")
        }

        // generate a group ID when creating a virtual node for an MGM default group.
        return if (groupId == GroupPolicyConstants.PolicyValues.Root.MGM_DEFAULT_GROUP_ID) {
            UUID.randomUUID().toString()
        } else {
            groupId
        }
    }

    override fun validateAndGetVirtualNode(virtualNodeShortId: String): VirtualNodeInfo {
        val vnode = virtualNodeInfoReadService.getByHoldingIdentityShortHash(parseShortHash(virtualNodeShortId))
            ?: throw ResourceNotFoundException("Virtual node", virtualNodeShortId)

        if (!isVirtualNodeInMaintenance(vnode)) {
            throw BadRequestException("Virtual node must be in maintenance to perform an upgrade.")
        }

        return vnode
    }

    override fun validateAndGetCpiByChecksum(cpiFileChecksum: String): CpiMetadata {
        val cpiByChecksum = cpiInfoReadService.getAll().filter {
            it.fileChecksum.toHexString().startsWith(cpiFileChecksum)
        }
        if (cpiByChecksum.isEmpty()) {
            throw ResourceNotFoundException("CPI", cpiFileChecksum)
        }
        return cpiByChecksum.single()
    }

    override fun validateCpiUpgradePrerequisites(currentCpi: CpiMetadata, upgradeCpi: CpiMetadata) {
        if (upgradeCpi.cpiId.name != currentCpi.cpiId.name) {
            throw BadRequestException("Upgrade CPI must have the same name as the current CPI.")
        }

        if (upgradeCpi.cpiId.signerSummaryHash != currentCpi.cpiId.signerSummaryHash) {
            throw BadRequestException("Upgrade CPI must have the same signature summary hash.")
        }
    }

    private fun isVirtualNodeInMaintenance(vnode: VirtualNodeInfo) =
        vnode.flowOperationalStatus == OperationalStatus.INACTIVE
                && vnode.flowStartOperationalStatus == OperationalStatus.INACTIVE
                && vnode.flowP2pOperationalStatus == OperationalStatus.INACTIVE
                && vnode.vaultDbOperationalStatus == OperationalStatus.INACTIVE

    private fun parseShortHash(virtualNodeShortId: String): ShortHash {
        return try {
            ShortHash.of(virtualNodeShortId)
        } catch (e: ShortHashException) {
            throw BadRequestException("Invalid holding identity short hash${e.message?.let { ": $it" }}")
        }
    }
}