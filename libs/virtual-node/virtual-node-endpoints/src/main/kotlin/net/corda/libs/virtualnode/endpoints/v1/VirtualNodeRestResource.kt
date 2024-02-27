package net.corda.libs.virtualnode.endpoints.v1

import net.corda.libs.virtualnode.endpoints.v1.types.ChangeVirtualNodeStateResponse
import net.corda.libs.virtualnode.endpoints.v1.types.CreateVirtualNodeRequest
import net.corda.libs.virtualnode.endpoints.v1.types.HoldingIdentity
import net.corda.libs.virtualnode.endpoints.v1.types.UpdateVirtualNodeDbRequest
import net.corda.libs.virtualnode.endpoints.v1.types.VirtualNodeInfo
import net.corda.libs.virtualnode.endpoints.v1.types.VirtualNodes
import net.corda.rest.RestResource
import net.corda.rest.annotations.ClientRequestBodyParameter
import net.corda.rest.annotations.HttpGET
import net.corda.rest.annotations.HttpPOST
import net.corda.rest.annotations.HttpPUT
import net.corda.rest.annotations.HttpRestResource
import net.corda.rest.annotations.RestApiVersion
import net.corda.rest.annotations.RestPathParameter
import net.corda.rest.annotations.RestQueryParameter
import net.corda.rest.asynchronous.v1.AsyncOperationStatus
import net.corda.rest.asynchronous.v1.AsyncResponse
import net.corda.rest.response.ResponseEntity

/** Rest operations for virtual node management. */
@HttpRestResource(
    name = "Virtual Node API",
    description = "The Virtual Nodes API consists of a number of endpoints to manage virtual nodes.",
    path = "virtualnode"
)
interface VirtualNodeRestResource : RestResource {

    /**
     * Requests the creation of a virtual node.
     *
     * @throws `HttpApiException` If the request returns an exceptional response.
     */
    @HttpPOST(
        title = "Create virtual node",
        description = "This method creates a new virtual node.",
        responseDescription = "The details of the created virtual node."
    )
    fun createVirtualNode(
        @ClientRequestBodyParameter(description = "Details of the virtual node to be created")
        request: CreateVirtualNodeRequest
    ): ResponseEntity<AsyncResponse>

    /**
     * Requests an update to an existing virtual node database.
     *
     * @throws `HttpApiException` If the request returns an exceptional response.
     */
    @HttpPUT(
        path = "{virtualNodeShortId}/db",
        title = "Update virtual node",
        description = "This method updates virtual node connection strings.",
        responseDescription = "The details of the updated virtual node.",
        minVersion = RestApiVersion.C5_2
    )
    fun updateVirtualNodeDb(
        @RestPathParameter(description = "Short ID of the virtual node instance to update")
        virtualNodeShortId: String,
        @ClientRequestBodyParameter(description = "Details of the virtual node to be updated")
        request: UpdateVirtualNodeDbRequest
    ): ResponseEntity<AsyncResponse>

    /**
     * Lists all virtual nodes onboarded to the cluster.
     *
     * @throws `HttpApiException` If the request returns an exceptional response.
     */
    @HttpGET(
        title = "Lists all virtual nodes",
        description = "This method lists all virtual nodes in the cluster.",
        responseDescription = "List of virtual node details."
    )
    fun getAllVirtualNodes(): VirtualNodes

    /**
     * Updates a virtual nodes state.
     *
     * @throws `VirtualNodeRPCOpsServiceException` If the virtual node update request could not be published.
     * @throws `HttpApiException` If the request returns an exceptional response.
     */
    @HttpPUT(
        path = "{virtualNodeShortId}/state/{newState}",
        title = "Update virtual node state",
        description = "This method updates the state of a new virtual node to one of the pre-defined values.",
        responseDescription = "Complete information about updated virtual node which will also contain the updated state."
    )
    fun updateVirtualNodeState(
        @RestPathParameter(description = "Short ID of the virtual node instance to update")
        virtualNodeShortId: String,
        @RestPathParameter(
            description = "State to transition virtual node instance into. " +
                "Possible values are: MAINTENANCE and ACTIVE."
        )
        newState: String
    ): ChangeVirtualNodeStateResponse

    /**
     * Returns the VirtualNodeInfo for a given [HoldingIdentity].
     *
     * @throws 'ResourceNotFoundException' If the virtual node was not found.
     * @throws `HttpApiException` If the request returns an exceptional response.
     */
    @HttpGET(
        path = "{holdingIdentityShortHash}",
        title = "Gets the VirtualNodeInfo for a HoldingIdentityShortHash",
        description = "This method returns the VirtualNodeInfo for a given Holding Identity ShortHash.",
        responseDescription = "VirtualNodeInfo for the specified virtual node."
    )
    fun getVirtualNode(
        @RestPathParameter(description = "The short hash of the holding identity; obtained during node registration")
        holdingIdentityShortHash: String
    ): VirtualNodeInfo

    /**
     * Returns the VirtualNodeOperationStatus for a given [requestId].
     */
    @HttpGET(
        path = "status/{requestId}",
        title = "Gets the VirtualNodeOperationStatus for an operation request id.",
        description = "This method returns the VirtualNodeOperationStatus for a given operation request id.",
        responseDescription = "VirtualNodeOperationStatus for the specified virtual node."
    )
    fun getVirtualNodeOperationStatus(
        @RestPathParameter(description = "The requestId for the operation; obtained during node creation/upgrade")
        requestId: String
    ): AsyncOperationStatus

    @HttpGET(
        path = "create/db/crypto",
        title = "Gets Crypto creation schema SQL",
        description = "This method returns the Crypto SQL needed for intention to create a virtual node.",
        responseDescription = "SQL needed to create the Crypto DB",
        minVersion = RestApiVersion.C5_2
    )
    fun getCreateCryptoSchemaSQL(): String

    @HttpGET(
        path = "create/db/uniqueness",
        title = "Gets Uniqueness creation schema SQL",
        description = "This method returns the Uniqueness SQL needed for intention to create a virtual node.",
        responseDescription = "SQL needed to create the Uniqueness DB",
        minVersion = RestApiVersion.C5_2
    )
    fun getCreateUniquenessSchemaSQL(): String

    @HttpGET(
        path = "create/db/vault/{cpiChecksum}",
        title = "Gets Vault creation schema SQL",
        description = "This method returns the Vault SQL needed for intention to create a virtual node and latest uploaded CPI.",
        responseDescription = "SQL needed to create the Vault DB and CPI",
        minVersion = RestApiVersion.C5_2
    )
    fun getCreateVaultSchemaSQL(
        @RestPathParameter(description = "The file checksum of the CPI")
        cpiChecksum: String,
    ): String

    @HttpGET(
        path = "{virtualNodeShortId}/db/vault/{newCpiChecksum}",
        title = "Gets migration schema SQL",
        description = "This method returns the SQL needed to update the virtual node's CPI",
        responseDescription = "SQL needed to bring schema up to date.",
        minVersion = RestApiVersion.C5_2
    )
    fun getUpdateSchemaSQL(
        @RestPathParameter(description = "Short ID of the virtual node instance")
        virtualNodeShortId: String,
        @RestPathParameter(description = "The file checksum of the CPI to be upgraded to")
        newCpiChecksum: String
    ): String

    /**
     * Asynchronous endpoint to upgrade a virtual node's CPI.
     */
    @Deprecated("Deprecated in favour of upgradeVirtualNode")
    @HttpPUT(
        path = "{virtualNodeShortId}/cpi/{targetCpiFileChecksum}",
        title = "Upgrade a virtual node's CPI.",
        description = "This method upgrades a virtual node's CPI.",
        responseDescription = "Identifier for the request.",
        minVersion = RestApiVersion.C5_0,
        maxVersion = RestApiVersion.C5_0
    )
    fun upgradeVirtualNodeDeprecated(
        @RestPathParameter(description = "Short ID of the virtual node instance to update")
        virtualNodeShortId: String,
        @RestPathParameter(description = "The file checksum of the CPI to upgrade to.")
        targetCpiFileChecksum: String
    ): ResponseEntity<AsyncResponse>

    /**
     * Asynchronous endpoint to upgrade a virtual node's CPI.
     */
    @HttpPUT(
        path = "{virtualNodeShortId}/cpi/{targetCpiFileChecksum}",
        title = "Upgrade a virtual node's CPI.",
        description = "This method upgrades a virtual node's CPI.",
        responseDescription = "Identifier for the request.",
        minVersion = RestApiVersion.C5_1
    )
    fun upgradeVirtualNode(
        @RestPathParameter(description = "Short ID of the virtual node instance to update")
        virtualNodeShortId: String,
        @RestPathParameter(description = "The file checksum of the CPI to upgrade to.")
        targetCpiFileChecksum: String,
        @RestQueryParameter(
            description = "Whether this upgrade should be forced regardless of OperationInProgress.",
            default = "false",
            required = false
        )
        forceUpgrade: Boolean = false
    ): ResponseEntity<AsyncResponse>
}
