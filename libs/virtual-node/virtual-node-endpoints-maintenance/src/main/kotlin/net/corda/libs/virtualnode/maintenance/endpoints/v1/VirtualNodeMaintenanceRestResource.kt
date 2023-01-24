package net.corda.libs.virtualnode.maintenance.endpoints.v1

import net.corda.httprpc.HttpFileUpload
import net.corda.httprpc.RestResource
import net.corda.httprpc.annotations.HttpPOST
import net.corda.httprpc.annotations.RestPathParameter
import net.corda.httprpc.annotations.HttpRestResource
import net.corda.libs.cpiupload.endpoints.v1.CpiUploadRestResource

/**
 * Maintenance RPC operations for virtual node management.
 *
 * Some of them could be highly disruptive, so great care should be taken when using them.
 */
@HttpRestResource(
    name = "Virtual Node Maintenance API",
    description = "The Virtual Node Maintenance API consists of a series of endpoints used for virtual node management." +
            "Warning: Using these endpoints could be highly disruptive, so great care should be taken when using them.",
    path = "maintenance/virtualnode"
)
interface VirtualNodeMaintenanceRestResource : RestResource {

    /**
     * HTTP POST to force upload of a CPI.
     *
     * Even if CPI with the same metadata has already been previously uploaded, this endpoint will overwrite earlier
     * stored CPI record.
     * The plugin purges any sandboxes running an overwritten version of a CPI.
     */
    @HttpPOST(
        path = "forceCpiUpload",
        title = "This method force uploads a CPI file.",
        description = "Even if CPI with the same metadata has already been uploaded, " +
                "this endpoint will overwrite the previously stored CPI record. This operation also purges any sandboxes running " +
                "an overwritten version of a CPI. This action can take some time to process, therefore it is performed asynchronously.",
        responseDescription = "The response ID which can be used to track the progress of the force CPI upload operation."
    )
    fun forceCpiUpload(upload: HttpFileUpload): CpiUploadRestResource.CpiUploadResponse

    @HttpPOST(
        path = "{virtualNodeShortId}/vault-schema/force-resync",
        title = "Resync the virtual node vault",
        description = "Rollback the virtual node database for the given virtual node short ID. Then apply current CPI " +
            "migrations. This operation is destructive and will result in user vault data being deleted, but will " +
            "not have any effect on system tables.",
        responseDescription = "A list of the shortIDs or the exception encountered"
    )
    fun resyncVirtualNodeDb(
        @RestPathParameter(description = "Short ID of the virtual node instance to rollback")
        virtualNodeShortId: String
    )
}
