package net.corda.membership.httprpc.v1

import net.corda.httprpc.RpcOps
import net.corda.httprpc.annotations.HttpRpcGET
import net.corda.httprpc.annotations.HttpRpcQueryParameter
import net.corda.httprpc.annotations.HttpRpcResource
import net.corda.membership.httprpc.v1.types.response.RegistrationRequestProgress

@HttpRpcResource(
    name = "MemberLookupRpcOps",
    description = "Membership Lookup APIs",
    path = "members"
)
interface MemberLookupRpcOps : RpcOps {
    /**
     * GET endpoint which returns the list of active members in the membership group.
     *
     * @return The known information of ACTIVE members.
     */
    @HttpRpcGET(
        description = "Lists the active members in the membership group."
    )
    fun lookup(
        @HttpRpcQueryParameter(description = "ID of the holding identity to be checked.")
        holdingIdentityId: String
    ): List<List<Map<String, String?>>>
}