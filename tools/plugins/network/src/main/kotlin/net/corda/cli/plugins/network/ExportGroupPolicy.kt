package net.corda.cli.plugins.network

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.cli.plugins.common.RestCommand
import net.corda.cli.plugins.typeconverter.ShortHashConverter
import net.corda.crypto.core.ShortHash
import net.corda.membership.rest.v1.MGMRestResource
import net.corda.sdk.network.ExportGroupPolicyFromMgm
import net.corda.sdk.rest.RestClientUtils.createRestClient
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.io.File
import kotlin.time.Duration.Companion.seconds

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
        converter = [ShortHashConverter::class],
    )
    lateinit var holdingIdentityShortHash: ShortHash

    override fun run() {
        exportGroupPolicy()
    }

    private fun exportGroupPolicy() {
        val restClient = createRestClient(
            MGMRestResource::class,
            insecure = insecure,
            minimumServerProtocolVersion = minimumServerProtocolVersion,
            username = username,
            password = password,
            targetUrl = targetUrl
        )
        val groupPolicyResponse = ExportGroupPolicyFromMgm().exportPolicy(
            restClient,
            holdingIdentityShortHash,
            wait = waitDurationSeconds.seconds
        )
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
