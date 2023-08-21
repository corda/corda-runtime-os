package net.corda.cli.plugins.network

import net.corda.cli.plugins.common.RestClientUtils.createRestClient
import net.corda.cli.plugins.common.RestCommand
import net.corda.cli.plugins.network.utils.InvariantUtils.checkInvariant
import net.corda.membership.rest.v1.MGMRestResource
import picocli.CommandLine.Option
import picocli.CommandLine.Command
import java.io.File
import com.fasterxml.jackson.databind.ObjectMapper

@Command(
    name = "export-group-policy",
    description = ["Export the group policy from the MGM"]
)
class ExportGroupPolicy : Runnable, RestCommand() {
    @Option(
        names = ["--save"],
        description = ["Save the exported group policy to a specific location"]
    )
    var saveLocation: String? = null

    @Option(
        names = ["-h", "--holding-identity-short-hash"],
        arity = "1",
        description = ["Short hash of the holding identity to be checked."]
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
                errorMessage = "Failed to export group policy after $MAX_ATTEMPTS attempts."
            ) {
                try {
                    val resource = client.start().proxy
                    resource.generateGroupPolicy(holdingIdentityShortHash!!)
                } catch (e: Exception) {
                    null
                }
            }
        }

        val groupPolicyFile = saveLocation?.let { File(it) } ?: getDefaultGroupPolicyFile()
        groupPolicyFile.parentFile.mkdirs()
        objectMapper.writerWithDefaultPrettyPrinter()
            .writeValue(
                groupPolicyFile,
                objectMapper.readTree(groupPolicyResponse)
            )
        println("Group policy exported and saved to: ${groupPolicyFile.absolutePath}")
    }

    private fun getDefaultGroupPolicyFile(): File {
        val defaultLocation = File(System.getProperty("user.home"), ".corda/groupPolicy.json")
        defaultLocation.parentFile.mkdirs()
        return defaultLocation
    }
}