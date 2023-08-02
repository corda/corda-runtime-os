package net.corda.e2etest.utilities

import com.fasterxml.jackson.databind.ObjectMapper

private fun createCertificate(certificateResource: String) = CpiLoader::class.java.classLoader.getResource(certificateResource)!!
    .readText()
    .replace("\r", "")
    .replace("\n", System.lineSeparator())

fun getDefaultStaticNetworkGroupPolicy(
    groupId: String,
    staticMemberNames: List<String>,
    certificateResource : String = "certificate.pem",
    customGroupParameters: Map<String, Any> = emptyMap(),
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
                },
                "groupParameters" to customGroupParameters
            )
        ),
        "p2pParameters" to mapOf(
            "sessionTrustRoots" to listOf(
                createCertificate(certificateResource),
                createCertificate(certificateResource)
            ),
            "tlsTrustRoots" to listOf(
                createCertificate(certificateResource)
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

fun getMgmGroupPolicy(): String {
    val groupPolicy = mapOf(
        "fileFormatVersion" to 1,
        "groupId" to "CREATE_ID",
        "registrationProtocol"
                to "net.corda.membership.impl.registration.dynamic.mgm.MGMRegistrationService",
        "synchronisationProtocol"
                to "net.corda.membership.impl.synchronisation.MgmSynchronisationServiceImpl"
    )
    return ObjectMapper().writeValueAsString(groupPolicy)
}