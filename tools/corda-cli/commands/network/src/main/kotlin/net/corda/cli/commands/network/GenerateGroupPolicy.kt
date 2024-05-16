package net.corda.cli.commands.network

import com.fasterxml.jackson.databind.exc.MismatchedInputException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import net.corda.cli.plugins.network.output.ConsoleOutput
import net.corda.cli.plugins.network.output.Output
import net.corda.cli.plugins.network.utils.PrintUtils.printJsonOutput
import net.corda.cli.plugins.network.utils.PrintUtils.verifyAndPrintError
import net.corda.cli.commands.typeconverter.X500NameConverter
import net.corda.membership.rest.v1.types.request.MemberRegistrationRequest
import net.corda.sdk.network.GenerateStaticGroupPolicy
import net.corda.v5.base.types.MemberX500Name
import org.yaml.snakeyaml.Yaml
import picocli.CommandLine
import java.nio.file.Path

/**
 * Subcommand for generating GroupPolicy.json file, containing the requirements for joining a group, can be used for
 * providing static membership information for mocking a membership group.
 */
@CommandLine.Command(
    name = "groupPolicy",
    description = ["Generates GroupPolicy.json file."],
    mixinStandardHelpOptions = true,
)
class GenerateGroupPolicy(private val output: Output = ConsoleOutput()) : Runnable {

    @CommandLine.Option(
        names = ["--endpoint"],
        arity = "0..1",
        description = ["Endpoint base URL"],
    )
    var endpoint: String? = null

    @CommandLine.Option(
        names = ["--endpoint-protocol"],
        arity = "0..1",
        description = ["Version of end-to-end authentication protocol"],
    )
    var endpointProtocol: Int? = null

    @CommandLine.Option(
        names = ["--name"],
        description = ["Member's X.500 name"],
        converter = [X500NameConverter::class],
    )
    var names: List<MemberX500Name>? = null

    @CommandLine.Option(
        names = ["--file", "-f"],
        arity = "0..1",
        description = ["Path to a JSON or YAML file that contains static network information"],
    )
    var filePath: Path? = null

    companion object {
        private const val MEMBER_STATUS_ACTIVE = "ACTIVE"
    }

    override fun run() {
        verifyAndPrintError {
            printJsonOutput(generateGroupPolicyContent(), output)
        }
    }

    /**
     * Creates the content of the GroupPolicy JSON.
     */
    private fun generateGroupPolicyContent(): Map<String, Any> {
        return GenerateStaticGroupPolicy().generateStaticGroupPolicy(members)
    }

    private val members by lazy {
        memberListFromInput() ?: GenerateStaticGroupPolicy.defaultMembers
    }

    /**
     * Returns a list of members generated from static network information provided via string parameters or file input.
     *
     * @throws IllegalArgumentException If both file input and string parameters are provided.
     */
    private fun memberListFromInput(): List<MemberRegistrationRequest>? {
        if (filePath != null) {
            require(endpoint == null) { "Endpoint may not be specified when '--file' is set." }
            require(endpointProtocol == null) { "Endpoint protocol may not be specified when '--file' is set." }
            require(names == null) { "Member name(s) may not be specified when '--file' is set." }
            return membersFromFile()
        }
        return membersFromStringParameters()
    }

    /**
     * Creates a list of members from static network information provided via file input.
     *
     * @return Member list or null if no file was provided.
     */
    @Suppress("ComplexMethod", "ThrowsCount")
    private fun membersFromFile(): List<MemberRegistrationRequest>? {
        val content = readAndValidateFile() ?: return null
        val members = mutableListOf<MemberRegistrationRequest>()
        content["memberNames"]?.let {
            (it as List<*>)
            it.forEach { name ->
                members.add(
                    MemberRegistrationRequest(
                        context = mapOf(
                            "name" to name.toString(),
                            "memberStatus" to MEMBER_STATUS_ACTIVE,
                            "endpointUrl-1" to content["endpoint"].toString(),
                            "endpointProtocol-1" to content["endpointProtocol"].toString(),
                        ),
                    )
                )
            }
        }
        content["members"]?.let { contentMembers ->
            (contentMembers as List<*>)
            contentMembers.forEach { member ->
                (member as Map<*, *>)
                val x500 = member["name"]?.toString() ?: throw IllegalArgumentException("No member name specified.")
                members.add(
                    MemberRegistrationRequest(
                        context = mapOf(
                            "name" to x500,
                            "memberStatus" to (member["status"]?.toString() ?: MEMBER_STATUS_ACTIVE),
                            "endpointUrl-1" to
                                (
                                    member["endpoint"]?.toString() ?: content["endpoint"]?.toString()
                                        ?: throw IllegalArgumentException("No endpoint specified.")
                                    ),
                            "endpointProtocol-1" to
                                (
                                    member["endpointProtocol"]?.toString() ?: content["endpointProtocol"]?.toString()
                                        ?: throw IllegalArgumentException("No endpoint protocol specified.")
                                    ),
                        ),
                    )
                )
            }
        }
        return members
    }

    /**
     * Creates a list of members from static network information provided via string parameters.
     *
     * @return Member list or null if no parameters were provided.
     */
    private fun membersFromStringParameters(): List<MemberRegistrationRequest>? {
        validateStringParameters()
        if (endpoint == null && endpointProtocol == null && names == null) {
            return null
        }
        return names?.let {
            GenerateStaticGroupPolicy().createMembersListFromListOfX500Names(it, endpoint!!, endpointProtocol!!)
        } ?: listOf()
    }

    /**
     * Validates string parameters specified for generating GroupPolicy.
     *
     * @throws IllegalArgumentException If member name(s) are provided without specifying endpoint information.
     */
    private fun validateStringParameters() {
        if (names != null) {
            require(endpoint != null) { "Endpoint must be specified using '--endpoint'." }
            require(endpointProtocol != null) { "Endpoint protocol must be specified using '--endpoint-protocol'." }
        }
    }

    /**
     * Reads and validates static network information from a JSON or YAML formatted file.
     *
     * @return Static network information as [Map], or null if no file was provided.
     * @throws IllegalArgumentException If the input file format is not supported, the file is malformed, or the file
     * contains an invalid combination of blocks e.g. both 'memberNames' and 'members' blocks are present.
     */
    @Suppress("ComplexMethod", "ThrowsCount")
    private fun readAndValidateFile(): Map<String, Any>? {
        return filePath?.toString()?.run {
            val file = filePath!!.toFile()
            if (!file.exists()) {
                throw IllegalArgumentException("No such file or directory: $this.")
            }
            when {
                endsWith(".json") -> {
                    try {
                        jacksonObjectMapper().readValue<Map<String, Any>>(file)
                    } catch (e: MismatchedInputException) {
                        throw IllegalArgumentException("Could not read static network information from $this.")
                    }
                }

                endsWith(".yaml") || endsWith(".yml") -> {
                    Yaml().load(file.readText())
                        ?: throw IllegalArgumentException("Could not read static network information from $this.")
                }

                else -> throw IllegalArgumentException("Input file format not supported.")
            }.also { parsed ->
                val hasMemberNames = parsed["memberNames"] != null
                val hasMembers = parsed["members"] != null
                if (hasMemberNames && hasMembers) {
                    throw IllegalArgumentException("Only one of 'memberNames' and 'members' blocks may be specified.")
                }
                if (hasMemberNames) {
                    require(parsed["endpoint"] != null) { "Endpoint must be specified." }
                    require(parsed["endpointProtocol"] != null) { "Endpoint protocol must be specified." }
                }
            }
        }
    }
}
