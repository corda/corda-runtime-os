package net.corda.membership.httprpc.v1

import net.corda.httprpc.RpcOps
import net.corda.httprpc.annotations.HttpRpcGET
import net.corda.httprpc.annotations.HttpRpcQueryParameter
import net.corda.httprpc.annotations.HttpRpcResource
import net.corda.membership.httprpc.v1.types.response.RegistrationRequestProgress

@HttpRpcResource(
    name = "MemberLookupRpcOps",
    description = "Membership Lookup APIs",
    path = "membership"
)
interface MemberLookupRpcOps : RpcOps {
    /**
     * GET endpoint which returns the list of active members in the membership group.
     *
     * @return [RegistrationRequestProgress] to indicate the last known status of the registration request based on
     *  local member data.
     */
    @HttpRpcGET(
        description = "Lists the active members in the membership group.",
        path = "members"
    )
    fun lookup(
        @HttpRpcQueryParameter(description = "ID of the holding identity to be checked.")
        holdingIdentityId: String
    ): List<List<List<Pair<String, String?>>>>
}