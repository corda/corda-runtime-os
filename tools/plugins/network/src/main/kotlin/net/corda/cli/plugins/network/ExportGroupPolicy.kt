package net.corda.cli.plugins.network

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.cli.plugins.common.RestCommand
import net.corda.cli.plugins.typeconverter.ShortHashConverter
import net.corda.crypto.core.ShortHash
import net.corda.restclient.CordaRestClient
import net.corda.sdk.network.ExportGroupPolicyFromMgm
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.io.File
import java.net.URI

@Command(
    name = "export-group-policy",
    description = ["Export the group policy from the MGM"],
    mixinStandardHelpOptions = true,
)
class ExportGroupPolicy : Runnable, RestCommand() {
    @Option(
        names = ["--save", "-s"],
        description = ["Location to save the group policy file to (defaults to ~/.corda/gp/groupPolicy.json)"],
    )
    var saveLocation: File = File(File(File(File(System.getProperty("user.home")), ".corda"), "gp"), "groupPolicy.json")

    @Option(
        names = ["-h", "--holding-identity-short-hash"],
        arity = "1",
        description = ["The holding identity short hash of the MGM."],
        required = true,
        converter = [ShortHashConverter::class],
    )
    lateinit var holdingIdentityShortHash: ShortHash

    override fun run() {
        super.run()
        exportGroupPolicy()
    }

    private fun exportGroupPolicy() {
        val restClient = CordaRestClient.createHttpClient(
            baseUrl = URI.create(targetUrl),
            username = username,
            password = password,
            insecure = insecure
        )
        val groupPolicyResponse = ExportGroupPolicyFromMgm(restClient).exportPolicy(holdingIdentityShortHash = holdingIdentityShortHash)
        saveLocation.parentFile.mkdirs()
        val objectMapper = ObjectMapper()
        objectMapper.writerWithDefaultPrettyPrinter()
            .writeValue(
                saveLocation,
                objectMapper.readTree(groupPolicyResponse),
            )
        println("Group policy exported and saved to: ${saveLocation.absolutePath}")
    }
}
