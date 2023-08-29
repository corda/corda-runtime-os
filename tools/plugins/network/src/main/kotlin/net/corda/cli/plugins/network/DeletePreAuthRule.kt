package net.corda.cli.plugins.network

import net.corda.cli.plugins.common.RestClientUtils.createRestClient
import net.corda.cli.plugins.common.RestCommand
import net.corda.membership.rest.v1.MGMRestResource
import picocli.CommandLine.Command
import picocli.CommandLine.Parameters

@Command(
    name = "delete-preauth-rule",
    description = ["Delete an approval rule for requests with pre-auth tokens"]
)
class DeletePreAuthRule : Runnable, RestCommand() {
    @Parameters(
        index = "0",
        description = ["The holding identity ID of the MGM"]
    )
    lateinit var holdingIdentityShortHash: String

    @Parameters(
        index = "1",
        description = ["The ID of the pre-auth rule to delete"]
    )
    lateinit var ruleId: String

    override fun run() {
        createRestClient(MGMRestResource::class).use { client ->
            client.start().proxy.deletePreAuthGroupApprovalRule(holdingIdentityShortHash, ruleId)
            println("Rule: $ruleId was deleted successfully")
        }
    }
}