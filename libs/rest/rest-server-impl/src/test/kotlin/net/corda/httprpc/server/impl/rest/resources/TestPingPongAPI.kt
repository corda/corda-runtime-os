package net.corda.httprpc.server.impl.rest.resources

import net.corda.httprpc.RestResource
import net.corda.httprpc.annotations.HttpPOST
import net.corda.httprpc.annotations.ClientRequestBodyParameter
import net.corda.httprpc.annotations.HttpRestResource

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