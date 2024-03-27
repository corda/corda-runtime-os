package net.corda.sdk.network

import net.corda.membership.rest.v1.types.request.MemberRegistrationRequest
import net.corda.v5.base.types.MemberX500Name
import java.util.*

class GenerateStaticGroupPolicy {
    companion object {
        private const val MEMBER_STATUS_ACTIVE = "ACTIVE"
        private const val NAME_KEY = "name"
        private const val MEMBER_STATUS_KEY = "memberStatus"
        private const val MEMBER_ENDPOINT_URL_1_KEY = "endpointUrl-1"
        private const val MEMBER_ENDPOINT_PROTOCOL_1_KEY = "endpointProtocol-1"

        val defaultMembers: List<MemberRegistrationRequest> by lazy {
            listOf(
                MemberRegistrationRequest(
                    context = mapOf(
                        NAME_KEY to "C=GB, L=London, O=Alice",
                        MEMBER_STATUS_KEY to MEMBER_STATUS_ACTIVE,
                        MEMBER_ENDPOINT_URL_1_KEY to "https://alice.corda5.r3.com:10000",
                        MEMBER_ENDPOINT_PROTOCOL_1_KEY to "1",
                    )
                ),
                MemberRegistrationRequest(
                    context = mapOf(
                        NAME_KEY to "C=GB, L=London, O=Bob",
                        MEMBER_STATUS_KEY to MEMBER_STATUS_ACTIVE,
                        MEMBER_ENDPOINT_URL_1_KEY to "https://bob.corda5.r3.com:10000",
                        MEMBER_ENDPOINT_PROTOCOL_1_KEY to "1",
                    )
                ),
                MemberRegistrationRequest(
                    context = mapOf(
                        NAME_KEY to "C=GB, L=London, O=Charlie",
                        MEMBER_STATUS_KEY to "SUSPENDED",
                        MEMBER_ENDPOINT_URL_1_KEY to "https://charlie.corda5.r3.com:10000",
                        MEMBER_ENDPOINT_PROTOCOL_1_KEY to "1",
                        "endpointUrl-2" to "https://charlie-dr.corda5.r3.com:10001",
                        "endpointProtocol-2" to "1",
                    )
                ),
            )
        }
    }

    /**
     * Create an object containing the necessary member info for a given list of X.500 names
     * @param names a list of X.500 names
     * @param endpointUrl the URL for all members, has default value
     * @param endpointProtocol protocol version for all members, has defaul value
     * @return member information as a List of Maps
     */
    fun createMembersListFromListOfX500Names(
        names: List<MemberX500Name>,
        endpointUrl: String = "https://member.corda5.r3.com:10000",
        endpointProtocol: Int = 1
    ): List<MemberRegistrationRequest> {
        return names.map { name ->
            MemberRegistrationRequest(
                context = mapOf(
                    NAME_KEY to name.toString(),
                    MEMBER_STATUS_KEY to MEMBER_STATUS_ACTIVE,
                    MEMBER_ENDPOINT_URL_1_KEY to endpointUrl,
                    MEMBER_ENDPOINT_PROTOCOL_1_KEY to endpointProtocol.toString(),
                )
            )
        }
    }

    /**
     * Create the static network policy for a given list of members
     * @param members list of member information, see [createMembersListFromListOfX500Names], has default value
     * @return static network policy
     */
    fun generateStaticGroupPolicy(members: List<MemberRegistrationRequest>? = defaultMembers): Map<String, Any> {
        return mapOf(
            "fileFormatVersion" to 1,
            "groupId" to UUID.randomUUID().toString(),
            "registrationProtocol" to "net.corda.membership.impl.registration.staticnetwork.StaticMemberRegistrationService",
            "synchronisationProtocol" to "net.corda.membership.impl.sync.staticnetwork.StaticMemberSyncService",
            "protocolParameters" to mapOf(
                "sessionKeyPolicy" to "Combined",
                "staticNetwork" to mapOf(
                    "members" to members?.map { it.context }
                ),
            ),
            "p2pParameters" to mapOf(
                "sessionTrustRoots" to listOf(
                    GenerateStaticGroupPolicy::class.java.getResource("/network/certificates/certificate0.pem")
                        .readText(),
                    GenerateStaticGroupPolicy::class.java.getResource("/network/certificates/certificate1.pem")
                        .readText(),
                    GenerateStaticGroupPolicy::class.java.getResource("/network/certificates/certificate2.pem")
                        .readText(),
                ),
                "tlsTrustRoots" to listOf(
                    GenerateStaticGroupPolicy::class.java.getResource("/network/certificates/certificate3.pem")
                        .readText(),
                    GenerateStaticGroupPolicy::class.java.getResource("/network/certificates/certificate4.pem")
                        .readText(),
                    GenerateStaticGroupPolicy::class.java.getResource("/network/certificates/certificate5.pem")
                        .readText(),
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
