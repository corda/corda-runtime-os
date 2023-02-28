package net.corda.rest.server.impl.rest.resources

import net.corda.rest.RestResource
import net.corda.rest.annotations.HttpPOST
import net.corda.rest.annotations.ClientRequestBodyParameter
import net.corda.rest.annotations.HttpRestResource

@HttpRestResource(
    name = "API",
    description = "Health Check"
)
interface TestPingPongAPI : RestResource {

    @HttpPOST
    fun ping(
        @ClientRequestBodyParameter(
            description = "Data",
            required = false
        ) data: PingPongData?
    ): String

    class PingPongData(val data: String)
}