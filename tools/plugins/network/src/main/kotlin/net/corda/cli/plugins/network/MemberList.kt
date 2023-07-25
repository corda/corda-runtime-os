package net.corda.cli.plugins.network

import com.fasterxml.jackson.core.util.DefaultIndenter
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import net.corda.cli.plugins.common.RestClientUtils.createRestClient
import net.corda.cli.plugins.common.RestCommand
import net.corda.membership.rest.v1.MemberLookupRestResource
import net.corda.membership.rest.v1.types.response.RestMemberInfo
import picocli.CommandLine

@CommandLine.Command(name = "members-list", description = ["Shows the list of members on the network."])
class MemberList(private val output: MemberListOutput = ConsoleMemberListOutput()) : RestCommand(), Runnable {

    @CommandLine.Option(
        names = ["-h", "--holding-identity-short-hash"],
        arity = "1",
        description = ["Short hash of the holding identity to be checked."]
    )
    var holdingIdentityShortHash: String? = null

    @CommandLine.Option(
        names = ["-cn"],
        arity = "0..1",
        description = ["Optional. Common Name (CN) attribute of the X.500 name to filter members by."]
    )
    var commonName: String? = null

    @CommandLine.Option(
        names = ["-ou"],
        arity = "0..1",
        description = ["Optional. Organization Unit (OU) attribute of the X.500 name to filter members by."]
    )
    var organizationUnit: String? = null

    @CommandLine.Option(
        names = ["-o"],
        arity = "0..1",
        description = ["Optional. Organization (O) attribute of the X.500 name to filter members by."]
    )
    var organization: String? = null

    @CommandLine.Option(
        names = ["-l"],
        arity = "0..1",
        description = ["Optional. Locality (L) attribute of the X.500 name to filter members by."]
    )
    var locality: String? = null

    @CommandLine.Option(
        names = ["-st"],
        arity = "0..1",
        description = ["Optional. State (ST) attribute of the X.500 name to filter members by."]
    )
    var state: String? = null

    @CommandLine.Option(
        names = ["-c"],
        arity = "0..1",
        description = ["Optional. Country (C) attribute of the X.500 name to filter members by."]
    )
    var country: String? = null

    private fun performMembersLookup(): String {
        requireNotNull(holdingIdentityShortHash) { "Holding identity short hash was not provided." }

        val result: List<RestMemberInfo> = createRestClient(MemberLookupRestResource::class).use { client ->
            val memberLookupProxy = client.start().proxy
            memberLookupProxy.lookup(
                holdingIdentityShortHash.toString(),
                commonName,
                organization,
                organizationUnit,
                locality,
                state,
                country
            ).members
        }

        return result.toString()
    }

    interface MemberListOutput {
        fun generateOutput(content: String)
    }

    class ConsoleMemberListOutput : MemberListOutput {
        /**
         * Receives the content of the file and prints it to the console output. It makes the testing easier.
         */
        override fun generateOutput(content: String) {
            content.lines().forEach {
                println(it)
            }
        }
    }

    override fun run() {
        val result = performMembersLookup()
        val objectMapper = jacksonObjectMapper()

        // add pretty printer and override indentation to make the nested values look better and the file more presentable
        val pp = DefaultPrettyPrinter()
        pp.indentArraysWith(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE)

        val jsonString = objectMapper.writer(pp).writeValueAsString(result)
        output.generateOutput(jsonString)
    }
}