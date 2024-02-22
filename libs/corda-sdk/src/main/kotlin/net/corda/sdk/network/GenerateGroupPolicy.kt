package net.corda.sdk.network

import java.util.*

class GenerateGroupPolicy {
    companion object {
        private const val MEMBER_STATUS_ACTIVE = "ACTIVE"

        val defaultMembers by lazy {
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
    }

    fun createMembersListFromListOfX500Strings(
        names: List<String>,
        endpointUrl: String = "https://member.corda5.r3.com:10000",
        endpointProtocol: Int = 1
    ): List<Map<String, Any>> {
        val members = mutableListOf<Map<String, Any>>()
        names.forEach { name ->
            members.add(
                mapOf(
                    "name" to name,
                    "memberStatus" to MEMBER_STATUS_ACTIVE,
                    "endpointUrl-1" to endpointUrl,
                    "endpointProtocol-1" to endpointProtocol,
                ),
            )
        }
        return members
    }
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
