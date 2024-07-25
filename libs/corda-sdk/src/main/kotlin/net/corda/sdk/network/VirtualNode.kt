package net.corda.sdk.network

import net.corda.crypto.core.ShortHash
import net.corda.libs.virtualnode.common.constant.VirtualNodeStateTransitions
import net.corda.restclient.CordaRestClient
import net.corda.restclient.generated.models.AsyncOperationStatus
import net.corda.restclient.generated.models.AsyncResponse
import net.corda.restclient.generated.models.ChangeVirtualNodeStateResponse
import net.corda.restclient.generated.models.JsonCreateVirtualNodeRequest
import net.corda.restclient.generated.models.VirtualNodeInfo
import net.corda.restclient.generated.models.VirtualNodes
import net.corda.sdk.data.Checksum
import net.corda.sdk.rest.RestClientUtils.executeWithRetry
import net.corda.v5.base.types.MemberX500Name
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class VirtualNode(val restClient: CordaRestClient) {

    /**
     * Request the creation of a virtual node
     * @param request of type JsonCreateVirtualNodeRequest to be used as the payload
     * @return an async response which you can get the id from
     */
    fun create(
        request: JsonCreateVirtualNodeRequest,
    ): AsyncResponse {
        return restClient.virtualNodeClient.postVirtualnode(request)
    }

    /**
     * Poll the virtual node until the status becomes ACTIVE
     * @param holdingId the holding identity of the node
     * @param wait Duration before timing out, default 30 seconds
     */
    fun waitForVirtualNodeToBeActive(
        holdingId: ShortHash,
        wait: Duration = 30.seconds
    ) {
        executeWithRetry(
            waitDuration = wait,
            operationName = "Wait for Virtual Node $holdingId to be active"
        ) {
            val response = restClient.virtualNodeClient.getVirtualnodeHoldingidentityshorthash(holdingId.value)
            response.flowP2pOperationalStatus == VirtualNodeInfo.FlowP2pOperationalStatus.ACTIVE
        }
    }

    /**
     * Combination method to create and wait for the virtual node to become ACTIVE
     * @param request of type JsonCreateVirtualNodeRequest to be used as the payload
     * @param wait Duration before timing out, default 30 seconds
     */
    fun createAndWaitForActive(
        request: JsonCreateVirtualNodeRequest,
        wait: Duration = 30.seconds
    ): ShortHash {
        val requestId = ShortHash.of(create(request).requestId)
        waitForVirtualNodeToBeActive(requestId, wait)
        return requestId
    }

    /**
     * Resync the virtual node vault
     * @param holdingId the holding identity of the node
     */
    fun resyncVault(
        holdingId: ShortHash,
    ) {
        restClient.virtualNodeMaintenanceClient.postMaintenanceVirtualnodeVirtualnodeshortidVaultSchemaForceResync(holdingId.value)
    }

    /**
     * List all existing virtual nodes
     * @param wait Duration before timing out, default 10 seconds
     * @return [VirtualNodes]
     */
    fun getAllVirtualNodes(
        wait: Duration = 10.seconds
    ): VirtualNodes {
        return executeWithRetry(
            waitDuration = wait,
            operationName = "List virtual nodes"
        ) {
            restClient.virtualNodeClient.getVirtualnode()
        }
    }

    fun waitForX500NameToAppearInListOfAllVirtualNodes(
        x500Name: MemberX500Name,
        wait: Duration = 30.seconds
    ) {
        executeWithRetry(
            waitDuration = wait,
            operationName = "Wait for x500 name to appear in list of virtual nodes"
        ) {
            val existingNodes = getAllVirtualNodes()
            if (existingNodes.virtualNodes.none { it.holdingIdentity.x500Name == x500Name.toString() }) {
                throw VirtualNodeLookupException("Failed to find virtual node with x500 name: $x500Name")
            }
        }
    }

    fun updateState(
        holdingId: ShortHash,
        state: VirtualNodeStateTransitions,
        wait: Duration = 30.seconds
    ): ChangeVirtualNodeStateResponse {
        return executeWithRetry(
            waitDuration = wait,
            operationName = "Update virtual node state"
        ) {
            restClient.virtualNodeClient.putVirtualnodeVirtualnodeshortidStateNewstate(holdingId.value, state.name)
        }
    }

    fun upgradeCpi(
        holdingId: ShortHash,
        cpiChecksum: Checksum,
        wait: Duration = 30.seconds,
    ): AsyncResponse {
        return executeWithRetry(
            waitDuration = wait,
            operationName = "Upgrade CPI for virtual node $holdingId"
        ) {
            restClient.virtualNodeClient.putVirtualnodeVirtualnodeshortidCpiTargetcpifilechecksum(holdingId.value, cpiChecksum.value)
        }
    }

    fun upgradeCpiAndWaitForSuccess(
        holdingId: ShortHash,
        cpiChecksum: Checksum,
        wait: Duration = 30.seconds,
    ) {
        val requestId = upgradeCpi(holdingId, cpiChecksum, wait).requestId
        val status = executeWithRetry(
            waitDuration = wait,
            operationName = "Wait for CPI upgrade to complete"
        ) {
            val response = restClient.virtualNodeClient.getVirtualnodeStatusRequestid(requestId)
            val inProgressStates = listOf(AsyncOperationStatus.Status.IN_PROGRESS, AsyncOperationStatus.Status.ACCEPTED)
            if (response.status in inProgressStates) {
                throw VirtualNodeUpgradeException("CPI upgrade status is still in progress: ${response.status}")
            }
            response.status
        }
        if (status != AsyncOperationStatus.Status.SUCCEEDED) {
            throw VirtualNodeUpgradeException("CPI upgrade failed with status: $status")
        }
    }
}

class VirtualNodeLookupException(message: String) : Exception(message)
class VirtualNodeUpgradeException(message: String) : Exception(message)
