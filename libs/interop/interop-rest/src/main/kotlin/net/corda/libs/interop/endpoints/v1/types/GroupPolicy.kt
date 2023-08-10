package net.corda.libs.interop.endpoints.v1.types

data class ProtocolParameters(
    val sessionKeyPolicy: String,
    val staticNetwork: StaticNetwork
)

data class StaticNetwork(
    val members: List<String>
)

data class P2pParameters(
    val sessionTrustRoots: List<String>,
    val tlsTrustRoots: List<String>,
    val sessionPki: String,
    val tlsPki: String,
    val tlsVersion: String,
    val protocolMode: String,
    val tlsType: String
)

data class GroupPolicy(
    val fileFormatVersion: Int,
    val groupId: String,
    val registrationProtocol: String,
    val synchronisationProtocol: String,
    val protocolParameters: ProtocolParameters,
    val p2pParameters: P2pParameters,
    val cipherSuite: Map<String, Any>
)
