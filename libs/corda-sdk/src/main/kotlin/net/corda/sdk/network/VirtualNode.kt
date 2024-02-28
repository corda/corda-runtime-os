package net.corda.sdk.network

import net.corda.libs.virtualnode.endpoints.v1.VirtualNodeRestResource
import net.corda.libs.virtualnode.endpoints.v1.types.CreateVirtualNodeRequestType.JsonCreateVirtualNodeRequest
import net.corda.libs.virtualnode.maintenance.endpoints.v1.VirtualNodeMaintenanceRestResource
import net.corda.rest.asynchronous.v1.AsyncResponse
import net.corda.rest.client.RestClient
import net.corda.rest.client.exceptions.MissingRequestedResourceException
import net.corda.rest.client.exceptions.RequestErrorException
import net.corda.rest.response.ResponseEntity
import net.corda.sdk.rest.InvariantUtils
import net.corda.virtualnode.OperationalStatus

class VirtualNode {

    fun create(restClient: RestClient<VirtualNodeRestResource>, request: JsonCreateVirtualNodeRequest): ResponseEntity<AsyncResponse> {
        return restClient.use { client ->
            InvariantUtils.checkInvariant(
                errorMessage = "Failed to request new virtual node creation after ${InvariantUtils.MAX_ATTEMPTS} attempts."
            ) {
                try {
                    val resource = client.start().proxy
                    resource.createVirtualNode(request)
                } catch (e: RequestErrorException) {
                    // This exception can be thrown while a request to create a virtual node is being made, so we
                    // catch it and re-try.
                    null
                }
            }
        }
    }

    fun waitForVirtualNodeToBeActive(restClient: RestClient<VirtualNodeRestResource>, holdingId: String) {
        restClient.use { client ->
            InvariantUtils.checkInvariant(
                errorMessage = "Virtual Node $holdingId is not active after ${InvariantUtils.MAX_ATTEMPTS} attempts."
            ) {
                try {
                    val resource = client.start().proxy.getVirtualNode(holdingId)
                    resource.flowP2pOperationalStatus == OperationalStatus.ACTIVE
                } catch (e: MissingRequestedResourceException) {
                    // This exception can be thrown while the Virtual Node is being processed, so we catch it and re-try.
                    null
                }
            }
        }
    }

    fun createAndWaitForActive(restClient: RestClient<VirtualNodeRestResource>, request: JsonCreateVirtualNodeRequest): String {
        val requestId = create(restClient, request).responseBody.requestId
        waitForVirtualNodeToBeActive(restClient, requestId)
        return requestId
    }

    fun resyncVault(restClient: RestClient<VirtualNodeMaintenanceRestResource>, holdingId: String) {
        restClient.use { client ->
            InvariantUtils.checkInvariant(
                errorMessage = "Unable to resync vault after ${InvariantUtils.MAX_ATTEMPTS} attempts."
            ) {
                try {
                    client.start().proxy.resyncVirtualNodeDb(holdingId)
                } catch (e: Exception) {
                    null
                }
            }
        }
    }
}
