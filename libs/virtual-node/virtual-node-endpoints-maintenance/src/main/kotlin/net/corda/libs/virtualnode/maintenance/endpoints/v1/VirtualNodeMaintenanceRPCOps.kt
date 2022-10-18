package net.corda.libs.virtualnode.maintenance.endpoints.v1

import net.corda.httprpc.HttpFileUpload
import net.corda.httprpc.RpcOps
import net.corda.httprpc.annotations.HttpRpcPOST
import net.corda.httprpc.annotations.HttpRpcPUT
import net.corda.httprpc.annotations.HttpRpcPathParameter
import net.corda.httprpc.annotations.HttpRpcResource
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

    @HttpRpcPOST(
        path = "resyncVault/{virtualNodeShortId}",
        title = "Resync the virtual node vault",
        description = "Rollback the virtual node database for the given virtual node short ID. Then apply current CPI " +
            "migrations. This operation is destructive and will resort in user vault data being deleted, but will " +
            "not have any effect on system tables.",
        responseDescription = "A list of the shortIDs or the exception encountered"
    )
    fun resyncVirtualNodeDb(
        @HttpRpcPathParameter(description = "Short ID of the virtual node instance to rollback")
        virtualNodeShortId: String
    ): List<String>

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
}
