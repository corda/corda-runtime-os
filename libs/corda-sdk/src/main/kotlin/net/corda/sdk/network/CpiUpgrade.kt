package net.corda.sdk.network

import net.corda.crypto.core.ShortHash
import net.corda.data.flow.output.FlowStates
import net.corda.libs.virtualnode.common.constant.VirtualNodeStateTransitions
import net.corda.restclient.CordaRestClient
import net.corda.restclient.generated.models.AsyncOperationStatus
import net.corda.restclient.generated.models.AsyncResponse
import net.corda.restclient.generated.models.VirtualNodeInfo
import net.corda.sdk.data.Checksum
import net.corda.sdk.network.config.NetworkConfig
import net.corda.sdk.packaging.CpiAttributes
import net.corda.sdk.rest.RestClientUtils.executeWithRetry
import net.corda.v5.base.types.MemberX500Name
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class CpiUpgrade(val restClient: CordaRestClient) {
    companion object {
        val terminatedFlowStates = listOf(FlowStates.COMPLETED, FlowStates.FAILED, FlowStates.KILLED).map { it.name }
    }

    private val virtualNode = VirtualNode(restClient)

    private fun validateNetworkConfig(networkConfig: NetworkConfig, upgradeCpiAttributes: CpiAttributes) {
        require(networkConfig.memberNodes.isNotEmpty()) { "Network configuration file does not contain any members to upgrade." }
        require(networkConfig.memberNodes.all { it.cpi == upgradeCpiAttributes.cpiName }) {
            "Network configuration file contains members with CPI name " +
                "which is different from the target CPI name '${upgradeCpiAttributes.cpiName}'"
        }
    }

    /**
     * Get the target existing virtual nodes for CPI upgrade based on the network configuration and the target CPI attributes.
     *
     * @param networkConfig the network configuration
     * @param upgradeCpiAttributes the target CPI attributes (name and version)
     * @param groupId the network group ID
     *
     * @return the list of target [VirtualNodeInfo] objects
     */
    fun getTargetVirtualNodes(networkConfig: NetworkConfig, upgradeCpiAttributes: CpiAttributes, groupId: String): List<VirtualNodeInfo> {
        validateNetworkConfig(networkConfig, upgradeCpiAttributes)

        val inferredFullHashes = networkConfig.memberNodes.map { it.getHoldingIdentityForGroup(groupId).fullHash }
        val targetVirtualNodes = virtualNode.getAllVirtualNodes().virtualNodes.filter {
            it.holdingIdentity.fullHash in inferredFullHashes
        }

        validateAllConfigMembersExist(networkConfig, targetVirtualNodes, upgradeCpiAttributes.cpiName, groupId)
        validateTargetVNodesCpiVersion(targetVirtualNodes, upgradeCpiAttributes)

        return targetVirtualNodes
    }

    private fun validateAllConfigMembersExist(
        networkConfig: NetworkConfig,
        targetVirtualNodes: List<VirtualNodeInfo>,
        upgradeCpiName: String,
        groupId: String,
    ) {
        val missingConfigMembers = networkConfig.memberNodes.filter { memberNode ->
            targetVirtualNodes.none {
                MemberX500Name.parse(it.holdingIdentity.x500Name) == MemberX500Name.parse(memberNode.x500Name) &&
                    it.cpiIdentifier.cpiName == upgradeCpiName
            }
        }

        require(missingConfigMembers.isEmpty()) {
            val missingMembersString = missingConfigMembers.joinToString("\n") {
                """Name: ${it.x500Name}, CPI: ${it.cpi}"""
            }
            "Failed to find following members from the network configuration file " +
                "among existing virtual nodes with target group $groupId:\n$missingMembersString"
        }
    }

    private fun validateTargetVNodesCpiVersion(targetVirtualNodes: List<VirtualNodeInfo>, upgradeCpiAttributes: CpiAttributes) {
        val sameCpiVersionMembers = targetVirtualNodes.filter {
            it.cpiIdentifier.cpiVersion == upgradeCpiAttributes.cpiVersion
        }
        require(sameCpiVersionMembers.isEmpty()) {
            val invalidMembersString = sameCpiVersionMembers.joinToString("\n") {
                "Name: ${it.holdingIdentity.x500Name}, CPI name: ${it.cpiIdentifier.cpiName}, " +
                    "CPI version: ${it.cpiIdentifier.cpiVersion}"
            }
            "One or more target virtual nodes have the same CPI version as the target CPI file:\n$invalidMembersString"
        }
    }

    /**
     * Upgrade the CPI on a virtual node and wait for the operation to complete:
     *
     *   - Set the virtual node state to MAINTENANCE
     *   - Wait until there are no running flows on the virtual node
     *   - Upgrade the CPI on the virtual node
     *   - Set the virtual node state to ACTIVE
     *
     * @param holdingId the holding identity of the virtual node
     * @param cpiChecksum the checksum of the target CPI file
     * @param timeout the [Duration] to wait for each of the steps to complete
     */
    fun upgradeCpiOnVirtualNode(holdingId: ShortHash, cpiChecksum: Checksum, timeout: Duration = 30.seconds) {
        virtualNode.updateState(holdingId, VirtualNodeStateTransitions.MAINTENANCE, timeout)

        waitUntilNoRunningFlows(holdingId, timeout)
        upgradeCpiAndWaitForSuccess(holdingId, cpiChecksum, timeout)

        virtualNode.updateState(holdingId, VirtualNodeStateTransitions.ACTIVE, timeout)
    }

    private fun waitUntilNoRunningFlows(holdingId: ShortHash, waitDuration: Duration = 30.seconds) {
        executeWithRetry(
            waitDuration = waitDuration,
            operationName = "Get all flows for the virtual node with holdingId $holdingId",
        ) {
            val flows = restClient.flowManagementClient.getFlowHoldingidentityshorthash(holdingId.value)
            val runningFlows = flows.flowStatusResponses.filter { it.flowStatus !in terminatedFlowStates }
            require(runningFlows.isEmpty()) {
                "There were running flows on the virtual node with holdingId $holdingId:\n" +
                    runningFlows.joinToString("\n")
            }
        }
    }

    private fun upgradeCpiAndWaitForSuccess(
        holdingId: ShortHash,
        cpiChecksum: Checksum,
        wait: Duration = 30.seconds,
    ) {
        val requestId = upgradeCpi(holdingId, cpiChecksum, wait).requestId
        val response = executeWithRetry(
            waitDuration = wait,
            operationName = "Wait for CPI upgrade to complete",
        ) {
            val response = restClient.virtualNodeClient.getVirtualnodeStatusRequestid(requestId)
            val inProgressStates = listOf(AsyncOperationStatus.Status.IN_PROGRESS, AsyncOperationStatus.Status.ACCEPTED)
            if (response.status in inProgressStates) {
                throw CpiUpgradeException("CPI upgrade status is still in progress: ${response.status}")
            }
            response
        }
        if (response.status != AsyncOperationStatus.Status.SUCCEEDED) {
            throw CpiUpgradeException("CPI upgrade failed: $response")
        }
    }

    private fun upgradeCpi(
        holdingId: ShortHash,
        cpiChecksum: Checksum,
        wait: Duration = 30.seconds,
    ): AsyncResponse {
        return executeWithRetry(
            waitDuration = wait,
            operationName = "Upgrade CPI for virtual node $holdingId",
        ) {
            restClient.virtualNodeClient.putVirtualnodeVirtualnodeshortidCpiTargetcpifilechecksum(holdingId.value, cpiChecksum.value)
        }
    }
}

class CpiUpgradeException(message: String) : Exception(message)
