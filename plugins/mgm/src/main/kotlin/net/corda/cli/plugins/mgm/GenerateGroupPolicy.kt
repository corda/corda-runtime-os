package net.corda.cli.plugins.mgm

import com.fasterxml.jackson.core.util.DefaultIndenter
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import picocli.CommandLine
import java.io.File

/**
 * Subcommand for generating GroupPolicy.json file, containing the requirements for joining a group, can be used for
 * providing static membership information for mocking a membership group.
 */
@CommandLine.Command(name = "groupPolicy", description = ["Generates GroupPolicy.json file."])
class GenerateGroupPolicy : Runnable {
    companion object {
        private const val PATH = "plugins/mgm/src/main/resources/GroupPolicy.json"
    }

    override fun run() {
        val objectMapper = jacksonObjectMapper()
        val groupPolicy = generateGroupPolicyContent()

        // add pretty printer and override indentation to make the nested values look better and the file more presentable
        val pp = DefaultPrettyPrinter()
        pp.indentArraysWith(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE)

        val jsonString = objectMapper.writer(pp).writeValueAsString(groupPolicy)
        println(jsonString)

        // create and write into json file under resources
        File(PATH).writeText(jsonString)
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
            Pair(
                "identityTrustStore",
                listOf(
                    "-----BEGIN CERTIFICATE-----\nMIICCDCJDBZFSiI=\n-----END CERTIFICATE-----\n",
                    "-----BEGIN CERTIFICATE-----\nMIIFPDCzIlifT20M\n-----END CERTIFICATE-----"
                )
            ),
            Pair(
                "tlsTrustStore",
                listOf(
                    "-----BEGIN CERTIFICATE-----\nMIIDxTCCE6N36B9K\n-----END CERTIFICATE-----\n",
                    "-----BEGIN CERTIFICATE-----\nMIIDdTCCKSZp4A==\n-----END CERTIFICATE-----",
                    "-----BEGIN CERTIFICATE-----\nMIIFPDCCIlifT20M\n-----END CERTIFICATE-----"
                )
            ),
            Pair(
                "mgmInfo",
                mapOf(
                    Pair("x500Name", "C=GB, L=London, O=Corda Network, OU=MGM, CN=Corda Network MGM"),
                    Pair(
                        "sessionKey",
                        "-----BEGIN PUBLIC KEY-----\nMFkwEwYHK+B3YGgcIALw==\n-----END PUBLIC KEY-----\n"
                    ),
                    Pair(
                        "certificate", listOf(
                            "-----BEGIN CERTIFICATE-----\nMIICxjCCRG11cu1\n-----END CERTIFICATE-----\n",
                            "-----BEGIN CERTIFICATE-----\nMIIB/TCCDJOIjhJ\n-----END CERTIFICATE-----\n",
                            "-----BEGIN CERTIFICATE-----\nMIICCDCCDZFSiI=\n-----END CERTIFICATE-----\n",
                            "-----BEGIN CERTIFICATE-----\nMIIFPDCClifT20M\n-----END CERTIFICATE-----"
                        )
                    ),
                    Pair("ecdhKey", "-----BEGIN PUBLIC KEY-----\nMCowBQYDH8Tc=\n-----END PUBLIC KEY-----\n"),
                    Pair(
                        "keys", listOf(
                            "-----BEGIN PUBLIC KEY-----\nMFkwEwYHgcIALw==\n-----END PUBLIC KEY-----\n"
                        )
                    ),
                    Pair(
                        "endpoints", listOf(
                            mapOf(
                                Pair("url", "https://mgm.corda5.r3.com:10000"),
                                Pair("protocolVersion", 1)
                            ),
                            mapOf(
                                Pair("url", "https://mgm-dr.corda5.r3.com:10000"),
                                Pair("protocolVersion", 1)
                            )
                        )
                    ),
                    Pair("platformVersion", 1),
                    Pair("softwareVersion", "5.0.0"),
                    Pair("serial", 1)
                )
            ),
            Pair("identityPKI", "Standard"),
            Pair("identityKeyPolicy", "Combined"),
            Pair(
                "cipherSuite", mapOf(
                    Pair("corda.provider", "default"),
                    Pair("corda.signature.provider", "default"),
                    Pair("corda.signature.default", "ECDSA_SECP256K1_SHA256"),
                    Pair("corda.signature.FRESH_KEYS", "ECDSA_SECP256K1_SHA256"),
                    Pair("corda.digest.default", "SHA256"),
                    Pair("corda.cryptoservice.provider", "default")
                )
            ),
            Pair(
                "roles", mapOf(
                    Pair(
                        "default", mapOf(
                            Pair("validator", "net.corda.v5.mgm.DefaultMemberInfoValidator"),
                            Pair("requiredMemberInfo", emptyList<Any>()),
                            Pair("optionalMemberInfo", emptyList<Any>())
                        )
                    ),
                    Pair(
                        "notary", mapOf(
                            Pair("validator", "net.corda.v5.mgm.NotaryMemberInfoValidator"),
                            Pair("requiredMemberInfo", listOf("notaryServiceParty")),
                            Pair("optionalMemberInfo", emptyList<Any>())
                        )
                    )
                )
            )
        )
        groupPolicy["staticMemberTemplate"] = listOf(
            mapOf(
                Pair("x500Name", "C=GB, L=London, O=Alice",),
                Pair("keyAlias", "alice-alias"),
                Pair("rotatedKeyAlias-1", "alice-historic-alias-1"),
                Pair("memberStatus", "ACTIVE"),
                Pair("endpointUrl-1", "https://alice.corda5.r3.com:10000"),
                Pair("endpointProtocol-1", 1)
            ),
            mapOf(
                Pair("x500Name", "C=GB, L=London, O=Bob"),
                Pair("keyAlias", "bob-alias"),
                Pair("rotatedKeyAlias-1", "bob-historic-alias-1"),
                Pair("rotatedKeyAlias-2", "bob-historic-alias-2"),
                Pair("memberStatus", "ACTIVE"),
                Pair("endpointUrl-1", "https://bob.corda5.r3.com:10000"),
                Pair("endpointProtocol-1", 1)
            ),
            mapOf(
                Pair("x500Name", "C=GB, L=London, O=Charlie"),
                Pair("keyAlias", "charlie-alias"),
                Pair("memberStatus", "SUSPENDED"),
                Pair("endpointUrl-1", "https://charlie.corda5.r3.com:10000"),
                Pair("endpointProtocol-1", 1),
                Pair("endpointUrl-2", "https://charlie-dr.corda5.r3.com:10001"),
                Pair("endpointProtocol-2", 1)
            )
        )
        return groupPolicy
    }
}