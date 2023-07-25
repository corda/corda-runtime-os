package net.corda.libs.interop.endpoints.v1.types


class CreateInteropIdentityRest {
    class Request(
        val applicationName: String,
        val groupPolicy: String,
        val members: List<ExportInteropIdentityRest.MemberData>? = null
    )

    class Response(
        val interopIdentityShortHash: String
    )
}
