package net.corda.libs.interop.endpoints.v1.types


class ImportInteropIdentityRest {
    class Request(
        val groupPolicy: String,
        val members: List<ExportInteropIdentityRest.MemberData>
    )
}
