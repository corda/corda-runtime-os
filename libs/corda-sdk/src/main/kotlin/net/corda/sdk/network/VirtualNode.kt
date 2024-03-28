package net.corda.sdk.network

import net.corda.crypto.core.ShortHash
import net.corda.libs.virtualnode.endpoints.v1.VirtualNodeRestResource
import net.corda.libs.virtualnode.endpoints.v1.types.CreateVirtualNodeRequestType.JsonCreateVirtualNodeRequest
import net.corda.libs.virtualnode.maintenance.endpoints.v1.VirtualNodeMaintenanceRestResource
import net.corda.rest.asynchronous.v1.AsyncResponse
import net.corda.rest.client.RestClient
import net.corda.rest.response.ResponseEntity
import net.corda.sdk.rest.RestClientUtils.executeWithRetry
import net.corda.virtualnode.OperationalStatus
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class VirtualNode {

    /**
     * Request the creation of a virtual node
     * @param restClient of type RestClient<VirtualNodeRestResource>
     * @param request of type JsonCreateVirtualNodeRequest to be used as the payload
     * @param wait Duration before timing out, default 10 seconds
     * @return an async response which you can get the id from
     */
    fun create(
        restClient: RestClient<VirtualNodeRestResource>,
        request: JsonCreateVirtualNodeRequest,
        wait: Duration = 10.seconds
    ): ResponseEntity<AsyncResponse> {
        return restClient.use { client ->
            executeWithRetry(
                waitDuration = wait,
                operationName = "Request new virtual node creation"
            ) {
                val resource = client.start().proxy
                resource.createVirtualNode(request)
            }
        }
    }

    /**
     * Poll the virtual node until the status becomes ACTIVE
     * @param restClient of type RestClient<VirtualNodeRestResource>
     * @param holdingId the holding identity of the node
     * @param wait Duration before timing out, default 30 seconds
     */
    fun waitForVirtualNodeToBeActive(
        restClient: RestClient<VirtualNodeRestResource>,
        holdingId: ShortHash,
        wait: Duration = 30.seconds
    ) {
        restClient.use { client ->
            executeWithRetry(
                waitDuration = wait,
                operationName = "Wait for Virtual Node $holdingId to be active"
            ) {
                val resource = client.start().proxy.getVirtualNode(holdingId.value)
                resource.flowP2pOperationalStatus == OperationalStatus.ACTIVE
            }
        }
    }

    /**
     * Combination method to create and wait for the virtual node to become ACTIVE
     * @param restClient of type RestClient<VirtualNodeRestResource>
     * @param request of type JsonCreateVirtualNodeRequest to be used as the payload
     * @param wait Duration before timing out, default 30 seconds
     */
    fun createAndWaitForActive(
        restClient: RestClient<VirtualNodeRestResource>,
        request: JsonCreateVirtualNodeRequest,
        wait: Duration = 30.seconds
    ): ShortHash {
        val requestId = with(create(restClient, request, wait)) {
            ShortHash.of(responseBody.requestId)
        }
        waitForVirtualNodeToBeActive(restClient, requestId, wait)
        return requestId
    }

    /**
     * Resync the virtual node vault
     * @param restClient of type RestClient<VirtualNodeMaintenanceRestResource>
     * @param holdingId the holding identity of the node
     * @param wait Duration before timing out, default 10 seconds
     */
    fun resyncVault(
        restClient: RestClient<VirtualNodeMaintenanceRestResource>,
        holdingId: ShortHash,
        wait: Duration = 10.seconds
    ) {
        restClient.use { client ->
            executeWithRetry(
                waitDuration = wait,
                operationName = "Resync vault"
            ) {
                client.start().proxy.resyncVirtualNodeDb(holdingId.value)
            }
        }
    }
}
