package net.corda.libs.virtualnode.endpoints.v1

import java.time.Instant
import net.corda.httprpc.RpcOps
import net.corda.httprpc.annotations.HttpRpcGET
import net.corda.httprpc.annotations.HttpRpcPOST
import net.corda.httprpc.annotations.HttpRpcPUT
import net.corda.httprpc.annotations.HttpRpcPathParameter
import net.corda.httprpc.annotations.HttpRpcRequestBodyParameter
import net.corda.httprpc.annotations.HttpRpcResource
import net.corda.httprpc.response.ResponseEntity
import net.corda.libs.virtualnode.endpoints.v1.types.VirtualNodeRequest
import net.corda.libs.virtualnode.endpoints.v1.types.VirtualNodes
import net.corda.libs.virtualnode.endpoints.v1.types.VirtualNodeInfo

/** RPC operations for virtual node management. */
@HttpRpcResource(
    name = "Virtual Node API",
    description = "The Virtual Nodes API consists of a number of endpoints to manage virtual nodes.",
    path = "virtualnode"
)
interface VirtualNodeRPCOps : RpcOps {

    /**
     * Creates a virtual node.
     *
     * @throws `VirtualNodeRPCOpsServiceException` If the virtual node creation request could not be published.
     * @throws `HttpApiException` If the request returns an exceptional response.
     */
    @HttpRpcPOST(
        title = "Create virtual node",
        description = "This method creates a new virtual node.",
        responseDescription = "The details of the created virtual node."
    )
    fun createVirtualNode(
        @HttpRpcRequestBodyParameter(description = "Details of the virtual node to be created")
        request: VirtualNodeRequest
    ): VirtualNodeInfo

    /**
     * Get a virtual node.
     */
    @HttpRpcGET(
        path = "{virtualNodeShortId}",
        description = "Get a virtual node.",
        responseDescription = "The details of the virtual node."
    )
    fun getVirtualNode(
        @HttpRpcPathParameter(description = "Short ID of the virtual node instance to update")
        virtualNodeShortId: String
    ): VirtualNodeInfo

    /**
     * Lists all virtual nodes onboarded to the cluster.
     *
     * @throws `HttpApiException` If the request returns an exceptional response.
     */
    @HttpRpcGET(
        title = "Lists all virtual nodes",
        description = "This method lists all virtual nodes in the cluster.",
        responseDescription = "List of virtual node details."
    )
    fun getAllVirtualNodes(): VirtualNodes


    @HttpRpcPUT(
        path = "{virtualNodeShortId}/cpi/{cpiFileChecksum}",
    )
    fun upgradeVirtualNodeCpi(
        @HttpRpcPathParameter(description = "Short ID of the virtual node instance to update")
        virtualNodeShortId: String,
        @HttpRpcPathParameter(description = "Checksum of the new version of the CPI to update to.")
        cpiFileChecksum: String
    ): ResponseEntity<AsyncResponse>

    @HttpRpcGET(
        path = "/status/{requestId}",
        title = "Get the status of an asynchronous virtual node maintenance request.",
        description = "Get the status of an asynchronous virtual node maintenance request.",
        responseDescription = "The details of the asynchronous request."
    )
    fun virtualNodeStatus(
        @HttpRpcPathParameter(description = "Identifier of the asynchronous request.")
        requestId: String
    ): ResponseEntity<UpgradeVirtualNodeStatus>
}

// todo move all these to the right places
class AsyncResponse(val requestId: String)

class UpgradeVirtualNodeStatus(
    val virtualNodeShortHash: String,
    val cpiFileChecksum: String,
    val stage: String?,
    startTime: Instant,
    endTime: Instant?,
    status: AsyncOperationStatus,
    errors: List<AsyncError>?
) : AsyncResponseStatus(startTime, endTime, status, errors)

open class AsyncResponseStatus(
    val startTime: Instant?,
    val endTime: Instant?,
    val status: AsyncOperationStatus,
    val errors: List<AsyncError>?
)

enum class AsyncOperationStatus {
    IN_PROGRESS, COMPLETE
}

data class AsyncError(
    val code: String,
    val message: String,
    val details: Map<String, String>?
)