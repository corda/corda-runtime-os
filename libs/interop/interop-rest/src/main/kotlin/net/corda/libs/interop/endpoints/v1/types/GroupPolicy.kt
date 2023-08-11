package net.corda.libs.interop.endpoints.v1.types

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
    val p2pParameters: P2pParameters
)
