package net.corda.cli.plugins.network

import net.corda.cli.plugins.common.RestClientUtils.createRestClient
import net.corda.cli.plugins.common.RestCommand
import net.corda.cli.plugins.network.output.ConsoleOutput
import net.corda.cli.plugins.network.output.Output
import net.corda.cli.plugins.network.utils.PrintUtils.Companion.printJsonOutput
import net.corda.membership.rest.v1.MemberLookupRestResource
import net.corda.membership.rest.v1.types.RestGroupParameters
import net.corda.v5.base.types.MemberX500Name
import picocli.CommandLine
import java.io.File

@CommandLine.Command(
    name = "group-parameters",
    description = ["Lookup group parameters."]
)
class GroupParameters(private val output: Output = ConsoleOutput()) : RestCommand(), Runnable {

    @CommandLine.Option(
        names = ["-h", "--holding-identity-short-hash"],
        arity = "1",
        description = ["Short hash of the holding identity performing the lookup."]
    )
    var holdingIdentityShortHash: String? = null

    @CommandLine.Option(
        names = ["-n", "--name"],
        arity = "1",
        description = ["X.500 name of the holding identity performing the lookup."]
    )
    var name: String? = null

    @CommandLine.Option(
        names = ["-g", "--group"],
        arity = "1",
        description = ["Group ID of holding identity performing the lookup. " +
                "Required if running this command with X.500 name. Defaults to last created group."]
    )
    var group: String? = null

    private fun performGroupParametersLookup(): RestGroupParameters {
        val holdingIdentity = getHoldingIdentity()
        val result: RestGroupParameters = createRestClient(MemberLookupRestResource::class).use { client ->
            val groupParametersProxy = client.start().proxy
            groupParametersProxy.viewGroupParameters(holdingIdentity)
        }
        return result
    }

    private fun getHoldingIdentity(): String {
        return holdingIdentityShortHash ?: name?.let {
            val x500Name = MemberX500Name.parse(it)
            val holdingIdentity = group?.let { group ->
                net.corda.virtualnode.HoldingIdentity(x500Name, group)
            } ?: net.corda.virtualnode.HoldingIdentity(x500Name, readDefaultGroup())
            holdingIdentity.shortHash.toString()
        } ?: throw IllegalArgumentException("Either 'holdingIdentityShortHash' or 'name' must be specified.")
    }

    private fun readDefaultGroup(): String {
        val groupIdFile = File(
            File(File(File(System.getProperty("user.home")), ".corda"), "groupId"),
            "groupId.txt"
        )
        return if (groupIdFile.exists()) {
            groupIdFile.readText().trim()
        } else {
            throw IllegalArgumentException("Group ID was not specified, and the last created group could not be found.")
        }
    }

    override fun run() {
        val result = performGroupParametersLookup()
        printJsonOutput(result, output)
    }
}