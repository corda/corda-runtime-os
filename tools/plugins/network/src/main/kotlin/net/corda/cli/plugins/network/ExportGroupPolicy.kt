package net.corda.cli.plugins.network

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.cli.plugins.common.RestClientUtils.createRestClient
import net.corda.cli.plugins.common.RestCommand
import net.corda.cli.plugins.network.utils.InvariantUtils.checkInvariant
import net.corda.membership.rest.v1.MGMRestResource
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.io.File

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
    )
    var holdingIdentityShortHash: String? = null

    override fun run() {
        exportGroupPolicy()
    }

    private fun exportGroupPolicy() {
        val objectMapper = ObjectMapper()
        val groupPolicyResponse = createRestClient(MGMRestResource::class).use { client ->
            checkInvariant(
                maxAttempts = MAX_ATTEMPTS,
                waitInterval = WAIT_INTERVAL,
                errorMessage = "Failed to export group policy after $MAX_ATTEMPTS attempts.",
            ) {
                try {
                    val resource = client.start().proxy
                    resource.generateGroupPolicy(holdingIdentityShortHash!!)
                } catch (e: Exception) {
                    null
                }
            }
        }

        saveLocation.parentFile.mkdirs()
        objectMapper.writerWithDefaultPrettyPrinter()
            .writeValue(
                saveLocation,
                objectMapper.readTree(groupPolicyResponse),
            )
        println("Group policy exported and saved to: ${saveLocation.absolutePath}")
    }
}
