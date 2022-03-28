package net.corda.membership.httprpc.v1

import net.corda.httprpc.RpcOps
import net.corda.httprpc.annotations.HttpRpcGET
import net.corda.httprpc.annotations.HttpRpcQueryParameter
import net.corda.httprpc.annotations.HttpRpcResource
import net.corda.membership.httprpc.v1.types.response.RpcMemberInfoList

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
        holdingIdentityId: String,
        @HttpRpcQueryParameter(name="cn", description = "CN attribute of the X.500 name to filter members by", required = false)
        commonName: String? = null,
        @HttpRpcQueryParameter(name="o", description = "O attribute of the X.500 name to filter members by", required = false)
        organisation: String? = null,
        @HttpRpcQueryParameter(name="ou", description = "OU attribute of the X.500 name to filter members by", required = false)
        organisationUnit: String? = null,
        @HttpRpcQueryParameter(name="l", description = "L attribute of the X.500 name to filter members by", required = false)
        locality: String? = null,
        @HttpRpcQueryParameter(name="st", description = "ST attribute of the X.500 name to filter members by", required = false)
        state: String? = null,
        @HttpRpcQueryParameter(name="c", description = "C attribute of the X.500 name to filter members by", required = false)
        country: String? = null
    ): RpcMemberInfoList
}