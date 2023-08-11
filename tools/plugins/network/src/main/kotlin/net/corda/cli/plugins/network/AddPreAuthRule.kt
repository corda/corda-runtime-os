package net.corda.cli.plugins.network

import net.corda.cli.plugins.common.RestClientUtils.createRestClient
import net.corda.cli.plugins.common.RestCommand
import net.corda.membership.rest.v1.MGMRestResource
import net.corda.membership.rest.v1.types.request.ApprovalRuleRequestParams
import picocli.CommandLine

@CommandLine.Command(
    name = "add-preauth-rule",
    description = ["Add an approval rule for requests with pre-auth tokens"]
)
class AddPreAuthRule : Runnable, RestCommand() {
    @CommandLine.Parameters(
        index = "0",
        description = ["The holding identity ID of the MGM"]
    )
    lateinit var holdingIdentityShortHash: String

    @CommandLine.Parameters(
        index = "1",
        description = ["The regular expression associated with the rule to be added"]
    )
    lateinit var ruleRegex: String

    @CommandLine.Parameters(
        index = "2",
        description = ["Optional. A label describing the rule to be added"]
    )
    var ruleLabel: String? = null

    override fun run() {
        val ruleParams = ApprovalRuleRequestParams(ruleRegex, ruleLabel)
        val approvalRuleInfo = createRestClient(MGMRestResource::class).use { client ->
            client.start().proxy.addPreAuthGroupApprovalRule(holdingIdentityShortHash, ruleParams)
        }
        println(approvalRuleInfo)
    }
}