package net.corda.sdk.network

import java.util.*

class GenerateGroupPolicy {
    fun generateStaticGroupPolicy(members: List<Map<String, Any>>?): Map<String, Any> {
        return mapOf(
            "fileFormatVersion" to 1,
            "groupId" to UUID.randomUUID().toString(),
            "registrationProtocol" to "net.corda.membership.impl.registration.staticnetwork.StaticMemberRegistrationService",
            "synchronisationProtocol" to "net.corda.membership.impl.sync.staticnetwork.StaticMemberSyncService",
            "protocolParameters" to mapOf(
                "sessionKeyPolicy" to "Combined",
                "staticNetwork" to mapOf(
                    "members" to members,
                ),
            ),
            "p2pParameters" to mapOf(
                "sessionTrustRoots" to listOf(
                    GenerateGroupPolicy::class.java.getResource("/network/certificates/certificate0.pem").readText(),
                    GenerateGroupPolicy::class.java.getResource("/network/certificates/certificate1.pem").readText(),
                    GenerateGroupPolicy::class.java.getResource("/network/certificates/certificate2.pem").readText(),
                ),
                "tlsTrustRoots" to listOf(
                    GenerateGroupPolicy::class.java.getResource("/network/certificates/certificate3.pem").readText(),
                    GenerateGroupPolicy::class.java.getResource("/network/certificates/certificate4.pem").readText(),
                    GenerateGroupPolicy::class.java.getResource("/network/certificates/certificate5.pem").readText(),
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
}
