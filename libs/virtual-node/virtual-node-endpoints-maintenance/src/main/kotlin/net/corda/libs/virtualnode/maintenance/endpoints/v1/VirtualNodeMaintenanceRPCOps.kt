package net.corda.libs.virtualnode.maintenance.endpoints.v1

import java.time.Instant
import net.corda.httprpc.HttpFileUpload
import net.corda.httprpc.RpcOps
import net.corda.httprpc.annotations.HttpRpcGET
import net.corda.httprpc.annotations.HttpRpcPOST
import net.corda.httprpc.annotations.HttpRpcPUT
import net.corda.httprpc.annotations.HttpRpcPathParameter
import net.corda.httprpc.annotations.HttpRpcResource
import net.corda.httprpc.response.ResponseEntity
import net.corda.libs.cpiupload.endpoints.v1.CpiUploadRPCOps
import net.corda.libs.virtualnode.maintenance.endpoints.v1.types.ChangeVirtualNodeStateResponse

/**
 * Maintenance RPC operations for virtual node management.
 *
 * Some of them could be highly disruptive, so great care should be taken when using them.
 */
@HttpRpcResource(
    name = "Virtual Node Maintenance API",
    description = "The Virtual Node Maintenance API consists of a series of endpoints used for virtual node management." +
            "Warning: Using these endpoints could be highly disruptive, so great care should be taken when using them.",
    path = "maintenance/virtualnode"
)
interface VirtualNodeMaintenanceRPCOps : RpcOps {

    /**
     * HTTP POST to force upload of a CPI.
     *
     * Even if CPI with the same metadata has already been previously uploaded, this endpoint will overwrite earlier
     * stored CPI record.
     * The plugin purges any sandboxes running an overwritten version of a CPI.
     */
    @HttpRpcPOST(
        path = "forceCpiUpload",
        title = "This method force uploads a CPI file.",
        description = "Even if CPI with the same metadata has already been uploaded, " +
                "this endpoint will overwrite the previously stored CPI record. This operation also purges any sandboxes running " +
                "an overwritten version of a CPI. This action can take some time to process, therefore it is performed asynchronously.",
        responseDescription = "The response ID which can be used to track the progress of the force CPI upload operation."
    )
    fun forceCpiUpload(upload: HttpFileUpload): CpiUploadRPCOps.CpiUploadResponse

    /**
     * Updates a virtual nodes state.
     *
     * @throws `VirtualNodeRPCOpsServiceException` If the virtual node update request could not be published.
     * @throws `HttpApiException` If the request returns an exceptional response.
     */
    @HttpRpcPUT(
        path = "{virtualNodeShortId}/state/{newState}",
        title = "Update virtual node state",
        description = "This method updates the state of a new virtual node to one of the pre-defined values.",
        responseDescription = "Complete information about updated virtual node which will also contain the updated state."
    )
    fun updateVirtualNodeState(
        @HttpRpcPathParameter(description = "Short ID of the virtual node instance to update")
        virtualNodeShortId: String,
        @HttpRpcPathParameter(description = "State to transition virtual node instance into. " +
                "Possible values are: IN_MAINTENANCE and ACTIVE.")
        newState: String
    ): ChangeVirtualNodeStateResponse

    /**
     * This should run db migrations for the new CPI.
     *
     * DBA beware to take backups of vault schema before running this command.
     */
    @HttpRpcPUT(
        path = "{virtualNodeShortId}/cpi/{cpiFileChecksum}",
    )
    fun upgradeVirtualNodeCpi(
        @HttpRpcPathParameter(description = "Short ID of the virtual node instance to update")
        virtualNodeShortId: String,
        @HttpRpcPathParameter(description = "Checksum of the new version of the CPI to update to.")
        cpiFileChecksum: String
    ): ResponseEntity<AsyncResponse> //todo conal - make this type available for both v1 endpoints - common v1 package

    @HttpRpcGET(
        path = "/status/{requestId}",
        title = "Get the status of an asynchronous virtual node maintenance request.",
        description = "Get the status of an asynchronous virtual node maintenance request.",
        responseDescription = "The details of the asynchronous request."
    )
    fun virtualNodeStatus(
        @HttpRpcPathParameter(description = "Identifier of the asynchronous request.")
        requestId: String
    ): ResponseEntity<StatusResponse>

}

data class AsyncResponse(
    /**
     * ID of the request, to be used in the status endpoint.
     */
    val requestId: String?,
    /**
     * Relative URL to the status endpoint.
     */
    val location: String?
)

data class StatusResponse(
    /**
     * ID of the request, or the idempotent client request identifier.
     */
    val requestId: String,
    /**
     * ID of the resource.
     */
    val resourceId: String?,
    /**
     * Status of the asynchronous operation.
     */
    val status: AsyncOperationStatus,
    /**
     * The time the request was made.
     */
    val startTime: Instant,
    /**
     * The time the request completed.
     */
    val endTime: Instant?,
    /**
     * Relative URL of the resource, a GET to this endpoint will return the created/updated resource.
     */
    val location: String?,
    /**
     * Recommended period (in ms) for retries.
     */
    val retryAfter: Long?,
    /**
     * Error information if the operation fails.
     */
    val error: AsyncError?
)

data class AsyncError(
    val code: String,
    val message: String,
    val details: Map<String, String>?
)

enum class AsyncOperationStatus {
    IN_PROGRESS, ERROR, COMPLETE
}