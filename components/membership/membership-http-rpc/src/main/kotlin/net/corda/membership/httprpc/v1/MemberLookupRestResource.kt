package net.corda.membership.httprpc.v1

import net.corda.httprpc.RestResource
import net.corda.httprpc.annotations.HttpRpcGET
import net.corda.httprpc.annotations.HttpRpcPathParameter
import net.corda.httprpc.annotations.HttpRpcQueryParameter
import net.corda.httprpc.annotations.HttpRpcResource
import net.corda.membership.httprpc.v1.types.response.RpcMemberInfoList

/**
 * The Member Lookup API consists of endpoints used to look up information related to membership groups. The API allows
 * you to retrieve a list of active and pending members in the membership group.
 */
@HttpRpcResource(
    name = "Member Lookup API",
    description = "The Member Lookup API consists of endpoints used to look up information related to membership groups.",
    path = "members"
)
interface MemberLookupRestResource : RestResource {
    /**
     * The [lookup] method enables you to retrieve a list of all active and pending members in the membership group that
     * are visible to the member represented by [holdingIdentityShortHash]. The list can be optionally filtered by
     * X.500 name attributes. This method returns an empty list if no members matching the criteria are found.
     *
     * Example usage:
     * ```
     * memberLookupOps.lookup(holdingIdentityShortHash = "58B6030FABDD")
     *
     * memberLookupOps.lookup(holdingIdentityShortHash = "58B6030FABDD", commonName = "Alice", country = "GB")
     * ```
     *
     * @param holdingIdentityShortHash Holding identity ID of the requesting member, which uniquely identifies the member
     * and its group. The result only contains members that are visible to this member.
     * @param commonName Optional. Common Name (CN) attribute of the X.500 name to filter members by.
     * @param organization Optional. Organization (O) attribute of the X.500 name to filter members by.
     * @param organizationUnit Optional. Organization Unit (OU) attribute of the X.500 name to filter members by.
     * @param locality Optional. Locality (L) attribute of the X.500 name to filter members by.
     * @param state Optional. State (ST) attribute of the X.500 name to filter members by.
     * @param country Optional. Country (C) attribute of the X.500 name to filter members by.
     *
     * @return List of active and pending members matching the criteria as [RpcMemberInfoList].
     */
    @HttpRpcGET(
        path = "{holdingIdentityShortHash}",
        description = "This method retrieves a list of all active and pending members in the membership group."
    )
    @Suppress("LongParameterList")
    fun lookup(
        @HttpRpcPathParameter(description = "Holding identity ID of the requesting member. The result only contains" +
                " members that are visible to this member")
        holdingIdentityShortHash: String,
        @HttpRpcQueryParameter(
            name = "cn",
            description = "Common Name (CN) attribute of the X.500 name to filter members by",
            required = false
        )
        commonName: String? = null,
        @HttpRpcQueryParameter(
            name = "o",
            description = "Organization (O) attribute of the X.500 name to filter members by",
            required = false
        )
        organization: String? = null,
        @HttpRpcQueryParameter(
            name = "ou",
            description = "Organization Unit (OU) attribute of the X.500 name to filter members by",
            required = false
        )
        organizationUnit: String? = null,
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
