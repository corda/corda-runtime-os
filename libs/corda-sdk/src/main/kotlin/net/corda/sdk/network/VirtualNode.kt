package net.corda.sdk.network

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

    fun waitForVirtualNodeToBeActive(restClient: RestClient<VirtualNodeRestResource>, holdingId: String, wait: Duration = 10.seconds) {
        restClient.use { client ->
            executeWithRetry(
                waitDuration = wait,
                operationName = "Wait for Virtual Node $holdingId to be active"
            ) {
                val resource = client.start().proxy.getVirtualNode(holdingId)
                resource.flowP2pOperationalStatus == OperationalStatus.ACTIVE
            }
        }
    }

    fun createAndWaitForActive(
        restClient: RestClient<VirtualNodeRestResource>,
        request: JsonCreateVirtualNodeRequest,
        wait: Duration = 10.seconds
    ): String {
        val requestId = create(restClient, request, wait).responseBody.requestId
        waitForVirtualNodeToBeActive(restClient, requestId, wait)
        return requestId
    }

    fun resyncVault(restClient: RestClient<VirtualNodeMaintenanceRestResource>, holdingId: String, wait: Duration = 10.seconds) {
        restClient.use { client ->
            executeWithRetry(
                waitDuration = wait,
                operationName = "Resync vault"
            ) {
                client.start().proxy.resyncVirtualNodeDb(holdingId)
            }
        }
    }
}
