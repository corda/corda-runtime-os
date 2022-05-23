package net.corda.libs.virtualnode.maintenance.endpoints.v1

import net.corda.httprpc.HttpFileUpload
import net.corda.httprpc.RpcOps
import net.corda.httprpc.annotations.HttpRpcPOST
import net.corda.httprpc.annotations.HttpRpcResource
import net.corda.libs.cpiupload.endpoints.v1.CpiUploadRPCOps

/**
 * Maintenance RPC operations for virtual node management.
 *
 * Some of them could be highly disruptive, so great care should be taken when using them.
 */
@HttpRpcResource(
    name = "VirtualNodeMaintenanceRPCOps",
    description = "Virtual node maintenance endpoints",
    path = "maintenance/virtualnode"
)
interface VirtualNodeMaintenanceRPCOps : RpcOps {

    /**
     * HTTP POST to force upload of a CPI.
     *
     * Even if CPI with the same metadata has already been previously uploaded, this endpoint will overwrite earlier
     * stored CPI record.
     * Furthermore, any sandboxes running an overwritten version of CPI will be purged and optionally vault data for
     * the affected Virtual Nodes wiped out.
     */
    @HttpRpcPOST(
        path = "forceCpiUpload",
        title = "Upload a CPI",
        description = "Uploads a CPI",
        responseDescription = "The request Id calculated for a CPI upload request"
    )
    fun forceCpiUpload(upload: HttpFileUpload): CpiUploadRPCOps.UploadResponse
}