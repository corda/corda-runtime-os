package net.corda.libs.interop.endpoints.v1.types


class ExportInteropIdentityRest {

    /** Data exported/imported per member */
    class MemberData(
        val x500Name: String,
        val owningIdentityShortHash: String,
        val endpointUrl: String,
        val endpointProtocol: String,
        val facadeIds: List<String>
    )

    class Response(
        val members: List<MemberData>,
        val groupPolicy: String
    )
}
