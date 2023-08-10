package net.corda.libs.interop.endpoints.v1.types


class ImportInteropIdentityRest3 {
    class Request(
        val applicationName: String,
        val groupPolicy: Map<String,Any>,
        val members: List<ExportInteropIdentityRest.MemberData>
    )
}
