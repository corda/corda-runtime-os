package net.corda.cli.plugins.mgm

import com.fasterxml.jackson.core.util.DefaultIndenter
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import picocli.CommandLine

/**
 * Subcommand for generating GroupPolicy.json file, containing the requirements for joining a group, can be used for
 * providing static membership information for mocking a membership group.
 */
@CommandLine.Command(name = "groupPolicy", description = ["Generates GroupPolicy.json file."])
class GenerateGroupPolicy(private val output: GroupPolicyOutput = ConsoleGroupPolicyOutput()) : Runnable {
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
     * Creates the content of the GroupPolicy json, for now it's just static group information, can be parameterized later.
     */
    private fun generateGroupPolicyContent(): Map<String, Any> {
        val groupPolicy = mutableMapOf<String, Any>()
        groupPolicy["fileFormatVersion"] = 1
        groupPolicy["groupId"] = "ABC123"
        groupPolicy["registrationProtocolFactory"] = "net.corda.v5.mgm.MGMRegistrationProtocolFactory"
        groupPolicy["synchronisationProtocolFactory"] = "net.corda.v5.mgm.MGMSynchronisationProtocolFactory"
        groupPolicy["protocolParameters"] = mutableMapOf(
            "identityTrustStore" to listOf(
                "-----BEGIN CERTIFICATE-----\nMIICCDCJDBZFSiI=\n-----END CERTIFICATE-----\n",
                "-----BEGIN CERTIFICATE-----\nMIIFPDCzIlifT20M\n-----END CERTIFICATE-----"
            ),
            "tlsTrustStore" to listOf(
                "-----BEGIN CERTIFICATE-----\nMIIDxTCCE6N36B9K\n-----END CERTIFICATE-----\n",
                "-----BEGIN CERTIFICATE-----\nMIIDdTCCKSZp4A==\n-----END CERTIFICATE-----",
                "-----BEGIN CERTIFICATE-----\nMIIFPDCCIlifT20M\n-----END CERTIFICATE-----"
            ),
            "mgmInfo" to mapOf(
                "name" to "C=GB, L=London, O=Corda Network, OU=MGM, CN=Corda Network MGM",
                "sessionKey" to "-----BEGIN PUBLIC KEY-----\nMFkwEwYHK+B3YGgcIALw==\n-----END PUBLIC KEY-----\n",
                "certificate" to listOf(
                    "-----BEGIN CERTIFICATE-----\nMIICxjCCRG11cu1\n-----END CERTIFICATE-----\n",
                    "-----BEGIN CERTIFICATE-----\nMIIB/TCCDJOIjhJ\n-----END CERTIFICATE-----\n",
                    "-----BEGIN CERTIFICATE-----\nMIICCDCCDZFSiI=\n-----END CERTIFICATE-----\n",
                    "-----BEGIN CERTIFICATE-----\nMIIFPDCClifT20M\n-----END CERTIFICATE-----"
                ),
                "ecdhKey" to "-----BEGIN PUBLIC KEY-----\nMCowBQYDH8Tc=\n-----END PUBLIC KEY-----\n",
                "keys" to listOf(
                    "-----BEGIN PUBLIC KEY-----\nMFkwEwYHgcIALw==\n-----END PUBLIC KEY-----\n"
                ),
                "endpoints" to listOf(
                    mapOf(
                        "url" to "https://mgm.corda5.r3.com:10000",
                        "protocolVersion" to 1
                    ),
                    mapOf(
                        "url" to "https://mgm-dr.corda5.r3.com:10000",
                        "protocolVersion" to 1
                    )
                ),
                "platformVersion" to 1,
                "softwareVersion" to "5.0.0",
                "serial" to 1
            ),
            "identityPKI" to "Standard",
            "identityKeyPolicy" to "Combined",
            "cipherSuite" to mapOf(
                "corda.provider" to "default",
                "corda.signature.provider" to "default",
                "corda.signature.default" to "ECDSA_SECP256K1_SHA256",
                "corda.signature.FRESH_KEYS" to "ECDSA_SECP256K1_SHA256",
                "corda.digest.default" to "SHA256",
                "corda.cryptoservice.provider" to "default"
            ),
            "roles" to mapOf(
                "default" to mapOf(
                    "validator" to "net.corda.v5.mgm.DefaultMemberInfoValidator",
                    "requiredMemberInfo" to emptyList<Any>(),
                    "optionalMemberInfo" to emptyList<Any>()
                ),
                "notary" to mapOf(
                    "validator" to "net.corda.v5.mgm.NotaryMemberInfoValidator",
                    "requiredMemberInfo" to listOf("notaryServiceParty"),
                    "optionalMemberInfo" to emptyList<Any>()
                )
            )
        )
        groupPolicy["staticNetwork"] = mapOf(
            "mgm" to mapOf(
                "keyAlias" to "mgm-alias"
            ),
            "members" to listOf(
                mapOf(
                    "name" to "C=GB, L=London, O=Alice",
                    "keyAlias" to "alice-alias",
                    "rotatedKeyAlias-1" to "alice-historic-alias-1",
                    "memberStatus" to "ACTIVE",
                    "endpointUrl-1" to "https://alice.corda5.r3.com:10000",
                    "endpointProtocol-1" to 1
                ),
                mapOf(
                    "name" to "C=GB, L=London, O=Bob",
                    "keyAlias" to "bob-alias",
                    "rotatedKeyAlias-1" to "bob-historic-alias-1",
                    "rotatedKeyAlias-2" to "bob-historic-alias-2",
                    "memberStatus" to "ACTIVE",
                    "endpointUrl-1" to "https://bob.corda5.r3.com:10000",
                    "endpointProtocol-1" to 1
                ),
                mapOf(
                    "name" to "C=GB, L=London, O=Charlie",
                    "keyAlias" to "charlie-alias",
                    "memberStatus" to "SUSPENDED",
                    "endpointUrl-1" to "https://charlie.corda5.r3.com:10000",
                    "endpointProtocol-1" to 1,
                    "endpointUrl-2" to "https://charlie-dr.corda5.r3.com:10001",
                    "endpointProtocol-2" to 1
                )
            )
        )
        return groupPolicy
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
        println(content)
    }
}