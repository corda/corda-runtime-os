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
     * @param holdingIdentityId ID of the holding identity to be checked.
     * @param commonName Optional. Common Name (CN) attribute of the X.500 name to filter members by.
     * @param organisation Optional. Organisation (O) attribute of the X.500 name to filter members by.
     * @param organisationUnit Optional. Organisation Unit (OU) attribute of the X.500 name to filter members by.
     * @param locality Optional. Locality (L) attribute of the X.500 name to filter members by.
     * @param state Optional. State (ST) attribute of the X.500 name to filter members by.
     * @param country Optional. Country (C) attribute of the X.500 name to filter members by.
     *
     * @return The known information of ACTIVE members.
     */
    @HttpRpcGET(
        description = "Lists the active members in the membership group."
    )
    @Suppress("LongParameterList")
    fun lookup(
        @HttpRpcQueryParameter(description = "ID of the holding identity to be checked.")
        holdingIdentityId: String,
        @HttpRpcQueryParameter(
            name = "cn",
            description = "Common Name (CN) attribute of the X.500 name to filter members by",
            required = false
        )
        commonName: String? = null,
        @HttpRpcQueryParameter(
            name = "o",
            description = "Organisation (O) attribute of the X.500 name to filter members by",
            required = false
        )
        organisation: String? = null,
        @HttpRpcQueryParameter(
            name = "ou",
            description = "Organisation Unit (OU) attribute of the X.500 name to filter members by",
            required = false
        )
        organisationUnit: String? = null,
        @HttpRpcQueryParameter(
            name = "l",
            description = "Locality (L) attribute of the X.500 name to filter members by",
            required = false
        )
        locality: String? = null,
        @HttpRpcQueryParameter(
            name = "st",
            description = "State (ST) attribute of the X.500 name to filter members by",
            required = false
        )
        state: String? = null,
        @HttpRpcQueryParameter(
            name = "c",
            description = "Country (C) attribute of the X.500 name to filter members by",
            required = false
        )
        country: String? = null
    ): RpcMemberInfoList
}