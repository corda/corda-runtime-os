package net.corda.e2etest.utilities

import com.fasterxml.jackson.databind.ObjectMapper

object GroupPolicyUtils {
    private fun createCertificate() = CpiLoader::class.java.classLoader.getResource("certificate.pem")!!
        .readText()
        .replace("\r", "")
        .replace("\n", System.lineSeparator())

    fun getDefaultStaticNetworkGroupPolicy(
        groupId: String,
        staticMemberNames: List<String>
    ): String {
        val groupPolicy = mapOf(
            "fileFormatVersion" to 1,
            "groupId" to groupId,
            "registrationProtocol"
                    to "net.corda.membership.impl.registration.staticnetwork.StaticMemberRegistrationService",
            "synchronisationProtocol"
                    to "net.corda.membership.impl.sync.staticnetwork.StaticMemberSyncService",
            "protocolParameters" to mapOf(
                "sessionKeyPolicy" to "Combined",
                "staticNetwork" to mapOf(
                    "members" to staticMemberNames.map {
                        mapOf(
                            "name" to it,
                            "memberStatus" to "ACTIVE",
                            "endpointUrl-1" to "http://localhost:1080",
                            "endpointProtocol-1" to 1
                        )
                    }
                )
            ),
            "p2pParameters" to mapOf(
                "sessionTrustRoots" to listOf(
                    createCertificate(),
                    createCertificate()
                ),
                "tlsTrustRoots" to listOf(
                    createCertificate()
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
                "corda.cryptoservice.provider" to "default"
            )
        )
        return ObjectMapper().writeValueAsString(groupPolicy)
    }
}