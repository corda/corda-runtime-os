package net.corda.libs.interop.endpoints.v1.types


class ImportInteropIdentityRest2 {
    class Request(
        val applicationName: String,
        val groupPolicy: GroupPolicy,
        val members: List<ExportInteropIdentityRest.MemberData>
    )
}
