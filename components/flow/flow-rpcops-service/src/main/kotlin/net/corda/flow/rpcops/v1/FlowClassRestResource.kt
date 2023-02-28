package net.corda.flow.rpcops.v1

import net.corda.flow.rpcops.v1.types.response.StartableFlowsResponse
import net.corda.rest.RestResource
import net.corda.rest.annotations.HttpGET
import net.corda.rest.annotations.RestPathParameter
import net.corda.rest.annotations.HttpRestResource

/** Rest operations for getting flow information from a vNode. */
@HttpRestResource(
    name = "Flow Info API",
    description = "The Flow Info API consists of a number of endpoints used to find out which flows can be invoked " +
            "using the Flow Management API for a given identity.",
    path = "flowclass"
)
interface FlowClassRestResource : RestResource {

    @HttpGET(
        path = "{holdingIdentityShortHash}",
        title = "Get Startable Flows",
        description = "This method gets all flows that can be used by the specified holding identity.",
        responseDescription = "The class names of all flows that can be run"
    )
    fun getStartableFlows(
        @RestPathParameter(description = "The short hash of the holding identity; " +
                "this is obtained during node registration")
        holdingIdentityShortHash: String
    ): StartableFlowsResponse
}
