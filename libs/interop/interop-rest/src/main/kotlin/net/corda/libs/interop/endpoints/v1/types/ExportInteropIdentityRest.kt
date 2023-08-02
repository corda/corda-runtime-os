package net.corda.libs.interop.endpoints.v1.types
import net.corda.v5.application.interop.facade.FacadeId

class ExportInteropIdentityRest {

    /** Data exported/imported per member */
    class MemberData(
        val x500Name: String,
        val owningIdentityShortHash: String,
        val endpointUrl: String,
        val endpointProtocol: String,
        val facadeIds: List<FacadeId>
    )

    class Response(
        val members: List<MemberData>,
        val groupPolicy: String
    )
}
