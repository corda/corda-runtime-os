package net.corda.cli.plugins.mgm

import com.fasterxml.jackson.core.util.DefaultIndenter
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.fasterxml.jackson.databind.exc.MismatchedInputException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.yaml.snakeyaml.Yaml
import picocli.CommandLine
import java.nio.file.Path
import java.util.UUID

/**
 * Subcommand for generating GroupPolicy.json file, containing the requirements for joining a group, can be used for
 * providing static membership information for mocking a membership group.
 */
@CommandLine.Command(name = "groupPolicy", description = ["Generates GroupPolicy.json file."])
class GenerateGroupPolicy(private val output: GroupPolicyOutput = ConsoleGroupPolicyOutput()) : Runnable {

    @CommandLine.Option(
        names = ["--endpoint"],
        arity = "0..1",
        description = ["Endpoint base URL"]
    )
    var endpoint: String? = null

    @CommandLine.Option(
        names = ["--endpoint-protocol"],
        arity = "0..1",
        description = ["Version of end-to-end authentication protocol"]
    )
    var endpointProtocol: Int? = null

    @CommandLine.Option(
        names = ["--name"],
        description = ["Member's X.500 name"]
    )
    var names: List<String>? = null

    @CommandLine.Option(
        names = ["--file", "-f"],
        arity = "0..1",
        description = ["Path to a JSON or YAML file that contains static network information"]
    )
    var filePath: Path? = null

    companion object {
        private const val MEMBER_STATUS_ACTIVE = "ACTIVE"
    }

    override fun run() {
        val objectMapper = jacksonObjectMapper()
        val groupPolicy = generateGroupPolicyContent()

        // add pretty printer and override indentation to make the nested values look better and the file more presentable
        val pp = DefaultPrettyPrinter()
        pp.indentArraysWith(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE)

        val jsonString = objectMapper.writer(pp).writeValueAsString(groupPolicy)
        output.generateOutput(jsonString)
    }

    /**
     * Creates the content of the GroupPolicy JSON.
     */
    private fun generateGroupPolicyContent(): Map<String, Any> {
        return mapOf(
            "fileFormatVersion" to 1,
            "groupId" to UUID.randomUUID().toString(),
            "registrationProtocol" to "net.corda.membership.impl.registration.staticnetwork.StaticMemberRegistrationService",
            "synchronisationProtocol" to "net.corda.membership.impl.sync.staticnetwork.StaticMemberSyncService",
            "protocolParameters" to mapOf(
                "sessionKeyPolicy" to "Combined",
                "staticNetwork" to mapOf(
                    "members" to members,
                )
            ),
            "p2pParameters" to mapOf(
                "sessionTrustRoots" to listOf(
                    GenerateGroupPolicy::class.java.getResource("/certificates/certificate0.pem").readText(),
                    GenerateGroupPolicy::class.java.getResource("/certificates/certificate1.pem").readText(),
                    GenerateGroupPolicy::class.java.getResource("/certificates/certificate2.pem").readText(),
                ),
                "tlsTrustRoots" to listOf(
                    GenerateGroupPolicy::class.java.getResource("/certificates/certificate3.pem").readText(),
                    GenerateGroupPolicy::class.java.getResource("/certificates/certificate4.pem").readText(),
                    GenerateGroupPolicy::class.java.getResource("/certificates/certificate5.pem").readText(),
                ),
                "sessionPki" to "Standard",
                "tlsPki" to "Standard",
                "tlsVersion" to "1.3",
                "protocolMode" to "Authenticated_Encryption",
                "tlsType" to "OneWay",
            ),
            "cipherSuite" to mapOf(
                "corda.provider" to "default",
                "corda.signature.provider" to "default",
                "corda.signature.default" to "ECDSA_SECP256K1_SHA256",
                "corda.signature.FRESH_KEYS" to "ECDSA_SECP256K1_SHA256",
                "corda.digest.default" to "SHA256",
                "corda.cryptoservice.provider" to "default",
            ),
        )
    }

    private val defaultMembers by lazy {
        listOf(
            mapOf(
                "name" to "C=GB, L=London, O=Alice",
                "memberStatus" to "ACTIVE",
                "endpointUrl-1" to "https://alice.corda5.r3.com:10000",
                "endpointProtocol-1" to 1,
            ),
            mapOf(
                "name" to "C=GB, L=London, O=Bob",
                "memberStatus" to "ACTIVE",
                "endpointUrl-1" to "https://bob.corda5.r3.com:10000",
                "endpointProtocol-1" to 1,
            ),
            mapOf(
                "name" to "C=GB, L=London, O=Charlie",
                "memberStatus" to "SUSPENDED",
                "endpointUrl-1" to "https://charlie.corda5.r3.com:10000",
                "endpointProtocol-1" to 1,
                "endpointUrl-2" to "https://charlie-dr.corda5.r3.com:10001",
                "endpointProtocol-2" to 1,
            ),
        )
    }

    private val members by lazy {
        memberListFromInput() ?: defaultMembers
    }

    /**
     * Returns a list of members generated from static network information provided via string parameters or file input.
     *
     * @throws IllegalArgumentException If both file input and string parameters are provided.
     */
    private fun memberListFromInput(): List<Map<String, Any>>? {
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
    private fun membersFromFile(): List<Map<String, Any>>? {
        val content = readAndValidateFile() ?: return null
        val members = mutableListOf<Map<String, Any>>()
        content["memberNames"]?.let {
            (it as List<*>)
            it.forEach { name ->
                members.add(
                    mapOf(
                        "name" to name!!,
                        "memberStatus" to MEMBER_STATUS_ACTIVE,
                        "endpointUrl-1" to content["endpoint"]!!,
                        "endpointProtocol-1" to content["endpointProtocol"]!!
                    )
                )
            }
        }
        content["members"]?.let {
            (it as List<*>)
            it.forEach { member ->
                (member as Map<*, *>)
                val x500 = member["name"]?.toString() ?: throw IllegalArgumentException("No member name specified.")
                members.add(
                    mapOf(
                        "name" to x500,
                        "memberStatus" to (member["status"] ?: MEMBER_STATUS_ACTIVE),
                        "endpointUrl-1" to (
                            member["endpoint"] ?: content["endpoint"]
                                ?: throw IllegalArgumentException("No endpoint specified.")
                            ),
                        "endpointProtocol-1" to (
                            member["endpointProtocol"] ?: content["endpointProtocol"]
                                ?: throw IllegalArgumentException("No endpoint protocol specified.")
                            )
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
    private fun membersFromStringParameters(): List<Map<String, Any>>? {
        validateStringParameters()
        if (endpoint == null && endpointProtocol == null && names == null) {
            return null
        }
        val members = mutableListOf<Map<String, Any>>()
        names?.forEach { name ->
            members.add(
                mapOf(
                    "name" to name,
                    "memberStatus" to MEMBER_STATUS_ACTIVE,
                    "endpointUrl-1" to endpoint!!,
                    "endpointProtocol-1" to endpointProtocol!!
                )
            )
        }
        return members
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

interface GroupPolicyOutput {
    fun generateOutput(content: String)
}

class ConsoleGroupPolicyOutput : GroupPolicyOutput {
    /**
     * Receives the content of the file and prints it to the console output. It makes the testing easier.
     */
    override fun generateOutput(content: String) {
        content.lines().forEach {
            println(it)
        }
    }
}
