package net.corda.cli.plugins.network

import net.corda.cli.plugins.common.RestClientUtils.createRestClient
import net.corda.cli.plugins.common.RestCommand
import net.corda.membership.rest.v1.MGMRestResource
import picocli.CommandLine.Command
import picocli.CommandLine.Parameters

@Command(
    name = "get-preauth-rules",
    description = ["Get the approval rules for requests with pre-auth tokens"]
)
class GetPreAuthRules : Runnable, RestCommand() {
    @Parameters(
        index = "0",
        description = ["The holding identity ID of the MGM"]
    )
    lateinit var holdingIdentityShortHash: String

    override fun run() {
        val approvalRules = createRestClient(MGMRestResource::class).use { client ->
            client.start().proxy.getPreAuthGroupApprovalRules(holdingIdentityShortHash)
        }
        println(approvalRules)
    }
}